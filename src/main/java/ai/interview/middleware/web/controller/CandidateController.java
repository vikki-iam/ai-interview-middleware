package ai.interview.middleware.web.controller;

import ai.interview.middleware.common.PageResponse;
import ai.interview.middleware.domain.enums.CandidateStatus;
import ai.interview.middleware.dto.candidate.CandidateRequest;
import ai.interview.middleware.dto.candidate.CandidateResponse;
import ai.interview.middleware.security.CurrentUserProvider;
import ai.interview.middleware.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Candidate CRUD and search.
 *
 * <p>Authorization is declared per endpoint rather than as one blanket rule: recruiters and
 * interviewers both maintain candidates, but deletion cascades to interviews and resumes, so it is
 * restricted to administrators.
 */
@RestController
@RequestMapping("/api/v1/candidates")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Candidates", description = "Manage candidates in the hiring pipeline")
public class CandidateController {

    private final CandidateService candidateService;
    private final CurrentUserProvider currentUserProvider;

    public CandidateController(
            CandidateService candidateService, CurrentUserProvider currentUserProvider) {
        this.candidateService = candidateService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Search candidates",
            description =
                    "Paged and sortable. `search` matches first name, last name, email, primary skill "
                            + "and current company, case-insensitively.")
    @ApiResponse(responseCode = "200", description = "A page of candidates")
    public ResponseEntity<PageResponse<CandidateResponse>> search(
            @Parameter(description = "Free-text search term", example = "kubernetes")
            @RequestParam(required = false)
            @Size(max = 100, message = "search term cannot exceed 100 characters")
            String search,
            @Parameter(description = "Filter by pipeline status") @RequestParam(required = false)
            CandidateStatus status,
            @Parameter(description = "Exact primary-skill match", example = "Terraform")
            @RequestParam(required = false)
            @Size(max = 100)
            String primarySkill,
            @Parameter(description = "Minimum years of experience", example = "5")
            @RequestParam(required = false)
            @DecimalMin(value = "0.0", message = "minExperience cannot be negative")
            BigDecimal minExperience,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                PageResponse.from(
                        candidateService.search(search, status, primarySkill, minExperience, pageable),
                        candidate -> candidate));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "Fetch one candidate, including resume and interview counts")
    @ApiResponse(responseCode = "200", description = "The candidate")
    @ApiResponse(responseCode = "404", description = "No such candidate")
    public ResponseEntity<CandidateResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(candidateService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "Create a candidate")
    @ApiResponse(responseCode = "201", description = "Created; the Location header points at the new resource")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "A candidate already exists with that email")
    public ResponseEntity<CandidateResponse> create(@Valid @RequestBody CandidateRequest request) {
        CandidateResponse created = candidateService.create(request, currentUserProvider.require());
        return ResponseEntity.created(URI.create("/api/v1/candidates/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Update a candidate",
            description = "Omitting `status` leaves the current status unchanged.")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "404", description = "No such candidate")
    @ApiResponse(responseCode = "409", description = "Another candidate already uses that email")
    public ResponseEntity<CandidateResponse> update(
            @PathVariable UUID id, @Valid @RequestBody CandidateRequest request) {
        return ResponseEntity.ok(candidateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete a candidate",
            description =
                    "Cascades to the candidate's resumes (including stored files) and interviews. "
                            + "Restricted to ADMIN.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "403", description = "Requires the ADMIN role")
    @ApiResponse(responseCode = "404", description = "No such candidate")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        candidateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
