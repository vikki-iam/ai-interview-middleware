package ai.interview.middleware.web.controller;

import ai.interview.middleware.common.PageResponse;
import ai.interview.middleware.domain.enums.InterviewStatus;
import ai.interview.middleware.dto.interview.AssignInterviewerRequest;
import ai.interview.middleware.dto.interview.GenerateQuestionsRequest;
import ai.interview.middleware.dto.interview.InterviewCreateRequest;
import ai.interview.middleware.dto.interview.InterviewQuestionResponse;
import ai.interview.middleware.dto.interview.InterviewResponse;
import ai.interview.middleware.dto.interview.InterviewResultRequest;
import ai.interview.middleware.dto.interview.InterviewResultResponse;
import ai.interview.middleware.dto.interview.InterviewStatusUpdateRequest;
import ai.interview.middleware.dto.interview.InterviewUpdateRequest;
import ai.interview.middleware.security.CurrentUserProvider;
import ai.interview.middleware.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview scheduling, AI question generation and results.
 *
 * <p>Read endpoints are open to every role because {@code InterviewService} scopes a {@code CANDIDATE}
 * to their own interviews inside the query. Write endpoints require ADMIN or INTERVIEWER.
 */
@RestController
@RequestMapping("/api/v1/interviews")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Interviews", description = "Schedule interviews, generate questions and record results")
public class InterviewController {

    private final InterviewService interviewService;
    private final CurrentUserProvider currentUserProvider;

    public InterviewController(
            InterviewService interviewService, CurrentUserProvider currentUserProvider) {
        this.interviewService = interviewService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(
            summary = "Search interviews",
            description =
                    "Paged and sortable. A CANDIDATE caller only ever sees interviews attached to their "
                            + "own candidate record, regardless of the filters supplied.")
    @ApiResponse(responseCode = "200", description = "A page of interviews")
    public ResponseEntity<PageResponse<InterviewResponse>> search(
            @Parameter(description = "Matches interview title, role title and candidate name")
            @RequestParam(required = false)
            @Size(max = 100)
            String search,
            @Parameter(description = "Filter by status") @RequestParam(required = false)
            InterviewStatus status,
            @Parameter(description = "Filter by candidate; ignored for CANDIDATE callers")
            @RequestParam(required = false)
            UUID candidateId,
            @Parameter(description = "Filter by assigned interviewer") @RequestParam(required = false)
            UUID interviewerId,
            @Parameter(description = "Earliest scheduled time, ISO-8601", example = "2026-08-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant scheduledFrom,
            @Parameter(description = "Latest scheduled time, ISO-8601", example = "2026-08-31T23:59:59Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant scheduledUntil,
            @ParameterObject
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                PageResponse.from(
                        interviewService.search(
                                search,
                                status,
                                candidateId,
                                interviewerId,
                                scheduledFrom,
                                scheduledUntil,
                                currentUserProvider.require(),
                                pageable),
                        interview -> interview));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Fetch one interview with its questions",
            description = "Model answers are withheld from CANDIDATE callers.")
    @ApiResponse(responseCode = "200", description = "The interview")
    @ApiResponse(responseCode = "403", description = "The interview belongs to another candidate")
    @ApiResponse(responseCode = "404", description = "No such interview")
    public ResponseEntity<InterviewResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(interviewService.findById(id, currentUserProvider.require()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "Schedule an interview")
    @ApiResponse(responseCode = "201", description = "Scheduled")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the interviewer is not eligible")
    @ApiResponse(responseCode = "404", description = "No such candidate or interviewer")
    public ResponseEntity<InterviewResponse> create(
            @Valid @RequestBody InterviewCreateRequest request) {
        InterviewResponse created = interviewService.create(request, currentUserProvider.require());
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Update an interview",
            description = "A completed or cancelled interview can no longer be edited.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Illegal status transition or terminal interview")
    @ApiResponse(responseCode = "404", description = "No such interview")
    public ResponseEntity<InterviewResponse> update(
            @PathVariable UUID id, @Valid @RequestBody InterviewUpdateRequest request) {
        return ResponseEntity.ok(interviewService.update(id, request));
    }

    @PatchMapping("/{id}/interviewer")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "Assign or reassign the interviewer")
    @ApiResponse(responseCode = "200", description = "Assigned")
    @ApiResponse(responseCode = "400", description = "The user cannot act as an interviewer")
    @ApiResponse(responseCode = "404", description = "No such interview or user")
    public ResponseEntity<InterviewResponse> assignInterviewer(
            @PathVariable UUID id, @Valid @RequestBody AssignInterviewerRequest request) {
        return ResponseEntity.ok(interviewService.assignInterviewer(id, request.interviewerId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Move an interview to a new status",
            description =
                    "Legal transitions: SCHEDULED to IN_PROGRESS, COMPLETED or CANCELLED; "
                            + "IN_PROGRESS to COMPLETED or CANCELLED. Terminal statuses are final.")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "400", description = "Illegal transition")
    @ApiResponse(responseCode = "404", description = "No such interview")
    public ResponseEntity<InterviewResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody InterviewStatusUpdateRequest request) {
        return ResponseEntity.ok(interviewService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete an interview",
            description = "Cascades to its questions and result. Restricted to ADMIN.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "Requires the ADMIN role")
    @ApiResponse(responseCode = "404", description = "No such interview")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        interviewService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/questions")
    @Operation(
            summary = "List an interview's questions",
            description = "Model answers are withheld from CANDIDATE callers.")
    @ApiResponse(responseCode = "200", description = "Questions in sequence order")
    @ApiResponse(responseCode = "404", description = "No such interview")
    public ResponseEntity<List<InterviewQuestionResponse>> listQuestions(@PathVariable UUID id) {
        return ResponseEntity.ok(interviewService.listQuestions(id, currentUserProvider.require()));
    }

    @PostMapping("/{id}/questions/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Generate questions with the AI service",
            description =
                    "Calls the AI service using the interview's role, level and focus skills unless "
                            + "overridden in the body. An empty body is valid. Previously generated "
                            + "questions are replaced by default; interviewer-authored questions are kept.")
    @ApiResponse(responseCode = "200", description = "The interview's full question list")
    @ApiResponse(responseCode = "400", description = "The interview is terminal or has no focus skills")
    @ApiResponse(responseCode = "404", description = "No such interview")
    @ApiResponse(responseCode = "503", description = "The AI service is unavailable")
    public ResponseEntity<List<InterviewQuestionResponse>> generateQuestions(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) GenerateQuestionsRequest request) {

        GenerateQuestionsRequest effective =
                request == null ? new GenerateQuestionsRequest(null, null, null) : request;
        return ResponseEntity.ok(
                interviewService.generateQuestions(id, effective, currentUserProvider.require()));
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Fetch the interview result")
    @ApiResponse(responseCode = "200", description = "The scorecard")
    @ApiResponse(responseCode = "403", description = "The interview belongs to another candidate")
    @ApiResponse(responseCode = "404", description = "No such interview, or no result submitted yet")
    public ResponseEntity<InterviewResultResponse> findResult(@PathVariable UUID id) {
        return ResponseEntity.ok(interviewService.findResult(id, currentUserProvider.require()));
    }

    @PostMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Submit or replace the interview result",
            description =
                    "Marks the interview COMPLETED. `overallScore` is derived server-side as the mean of "
                            + "the three dimension scores. Re-submitting overwrites the existing result.")
    @ApiResponse(responseCode = "200", description = "Result recorded")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the interview is cancelled")
    @ApiResponse(responseCode = "404", description = "No such interview")
    public ResponseEntity<InterviewResultResponse> submitResult(
            @PathVariable UUID id, @Valid @RequestBody InterviewResultRequest request) {
        return ResponseEntity.ok(
                interviewService.submitResult(id, request, currentUserProvider.require()));
    }
}
