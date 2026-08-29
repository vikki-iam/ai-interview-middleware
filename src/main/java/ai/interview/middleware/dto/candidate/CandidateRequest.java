package ai.interview.middleware.dto.candidate;

import ai.interview.middleware.domain.enums.CandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create/update payload for a candidate.
 *
 * <p>Constraints mirror the {@code candidates} CHECK constraints so an invalid value is rejected with
 * a field-level 400 rather than a 500 from a constraint violation at flush time.
 */
@Schema(name = "CandidateRequest", description = "Candidate create or update payload")
public record CandidateRequest(
        @Schema(example = "Neha", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "firstName is required")
        @Size(max = 80)
        String firstName,

        @Schema(example = "Gupta", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "lastName is required")
        @Size(max = 80)
        String lastName,

        @Schema(example = "neha.gupta@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "email is required")
        @Email(message = "must be a well-formed email address")
        @Size(max = 255)
        String email,

        @Schema(example = "+91-98200-11201")
        @Size(max = 30)
        @Pattern(
                regexp = "^$|^[+0-9][0-9 ()\\-]{6,29}$",
                message = "phone may contain digits, spaces, parentheses and hyphens only")
        String phone,

        @Schema(example = "Infobell Systems") @Size(max = 150) String currentCompany,

        @Schema(example = "Senior DevOps Engineer") @Size(max = 150) String currentPosition,

        @Schema(example = "7.5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "yearsOfExperience is required")
        @DecimalMin(value = "0.0", message = "yearsOfExperience cannot be negative")
        @DecimalMax(value = "60.0", message = "yearsOfExperience cannot exceed 60")
        @Digits(integer = 3, fraction = 1)
        BigDecimal yearsOfExperience,

        @Schema(example = "Kubernetes", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "primarySkill is required")
        @Size(max = 100)
        String primarySkill,

        @Schema(example = "Bengaluru, IN") @Size(max = 120) String location,

        @Schema(example = "INTERVIEWING", description = "Defaults to NEW on create")
        CandidateStatus status,

        @Schema(example = "Referred by Priya.") @Size(max = 4000) String notes) {}
