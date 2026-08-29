package ai.interview.middleware.web.controller;

import ai.interview.middleware.dto.resume.ResumeResponse;
import ai.interview.middleware.security.CurrentUserProvider;
import ai.interview.middleware.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resume upload and retrieval.
 *
 * <p>Upload is nested under the candidate because a resume has no meaning without one; retrieval and
 * deletion are addressed by resume id, since that is what the upload response hands back.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Resumes", description = "Upload and retrieve candidate resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final CurrentUserProvider currentUserProvider;

    public ResumeController(ResumeService resumeService, CurrentUserProvider currentUserProvider) {
        this.resumeService = resumeService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(path = "/candidates/{candidateId}/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Upload a resume",
            description =
                    "Multipart upload under the part name `file`. Accepts PDF, DOC, DOCX and plain text "
                            + "up to 10 MB. Metadata is stored in PostgreSQL; the bytes go to local disk "
                            + "or S3 depending on `app.storage.type`.")
    @ApiResponse(responseCode = "201", description = "Stored")
    @ApiResponse(responseCode = "400", description = "Empty file, unsupported type, or over the size limit")
    @ApiResponse(responseCode = "404", description = "No such candidate")
    @ApiResponse(responseCode = "413", description = "Body exceeded the servlet multipart limit")
    public ResponseEntity<ResumeResponse> upload(
            @PathVariable UUID candidateId, @RequestPart("file") MultipartFile file) {

        ResumeResponse stored =
                resumeService.upload(candidateId, file, currentUserProvider.require());
        return ResponseEntity.created(URI.create("/api/v1/resumes/" + stored.id())).body(stored);
    }

    @GetMapping("/candidates/{candidateId}/resumes")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "List a candidate's resumes, newest first")
    @ApiResponse(responseCode = "200", description = "Resume metadata")
    @ApiResponse(responseCode = "404", description = "No such candidate")
    public ResponseEntity<List<ResumeResponse>> listForCandidate(@PathVariable UUID candidateId) {
        return ResponseEntity.ok(resumeService.listForCandidate(candidateId));
    }

    @GetMapping("/resumes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "Fetch resume metadata")
    @ApiResponse(responseCode = "200", description = "Resume metadata")
    @ApiResponse(responseCode = "404", description = "No such resume")
    public ResponseEntity<ResumeResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(resumeService.findById(id));
    }

    @GetMapping("/resumes/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(
            summary = "Download the resume file",
            description = "Streams the stored bytes with a Content-Disposition attachment header.")
    @ApiResponse(responseCode = "200", description = "The file")
    @ApiResponse(responseCode = "404", description = "No such resume, or its stored bytes are missing")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        ResumeService.ResumeDownload download = resumeService.download(id);

        // Built rather than concatenated so a filename with a comma or quote cannot break the header,
        // and so non-ASCII names are RFC 5987 encoded rather than mangled.
        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.content().length)
                .body(new ByteArrayResource(download.content()));
    }

    @DeleteMapping("/resumes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INTERVIEWER')")
    @Operation(summary = "Delete a resume and its stored file")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such resume")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        resumeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
