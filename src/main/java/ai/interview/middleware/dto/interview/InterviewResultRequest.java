package ai.interview.middleware.dto.interview;

import ai.interview.middleware.domain.enums.Recommendation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Interview scorecard.
 *
 * <p>There is no {@code overallScore} field: it is derived server-side as the mean of the three
 * dimensions so the headline number can never contradict the breakdown.
 */
@Schema(name = "InterviewResultRequest", description = "Submits or replaces an interview result")
public record InterviewResultRequest(
        @Schema(example = "8.5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "technicalScore is required")
        @DecimalMin(value = "0.0", message = "scores range from 0 to 10")
        @DecimalMax(value = "10.0", message = "scores range from 0 to 10")
        @Digits(integer = 2, fraction = 1)
        BigDecimal technicalScore,

        @Schema(example = "8.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "communicationScore is required")
        @DecimalMin(value = "0.0", message = "scores range from 0 to 10")
        @DecimalMax(value = "10.0", message = "scores range from 0 to 10")
        @Digits(integer = 2, fraction = 1)
        BigDecimal communicationScore,

        @Schema(example = "9.0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "problemSolvingScore is required")
        @DecimalMin(value = "0.0", message = "scores range from 0 to 10")
        @DecimalMax(value = "10.0", message = "scores range from 0 to 10")
        @Digits(integer = 2, fraction = 1)
        BigDecimal problemSolvingScore,

        @Schema(example = "STRONG_HIRE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "recommendation is required")
        Recommendation recommendation,

        @Schema(example = "Excellent Kubernetes debugging instincts.")
        @Size(max = 4000)
        String strengths,

        @Schema(example = "Limited exposure to service mesh traffic policies.")
        @Size(max = 4000)
        String improvements,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "feedback is required")
        @Size(min = 20, max = 8000, message = "feedback must be between 20 and 8000 characters")
        String feedback) {}
