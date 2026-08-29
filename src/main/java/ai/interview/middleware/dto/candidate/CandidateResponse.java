package ai.interview.middleware.dto.candidate;

import ai.interview.middleware.domain.enums.CandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "CandidateResponse", description = "A candidate in the hiring pipeline")
public record CandidateResponse(
        UUID id,
        String firstName,
        String lastName,
        @Schema(description = "Convenience concatenation for list views") String fullName,
        String email,
        String phone,
        String currentCompany,
        String currentPosition,
        BigDecimal yearsOfExperience,
        String primarySkill,
        String location,
        CandidateStatus status,
        String notes,
        // Counts require an extra aggregate per candidate, so they are populated only on the
        // single-candidate endpoints. Omitted from JSON (not zero) in list responses, which keeps
        // the list query free of an N+1 count per row.
        @Schema(description = "Resumes on file; present only on GET /candidates/{id}") Long resumeCount,
        @Schema(description = "Interviews for this candidate; present only on GET /candidates/{id}")
        Long interviewCount,
        Instant createdAt,
        Instant updatedAt) {}
