package ai.interview.middleware.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Optional overrides for AI question generation.
 *
 * <p>An empty body is valid: the interview's own role, level and focus skills are used, which is the
 * common case.
 */
@Schema(name = "GenerateQuestionsRequest", description = "Overrides for a generation run")
public record GenerateQuestionsRequest(
        @Schema(example = "5", defaultValue = "5")
        @Min(value = 1, message = "questionCount must be at least 1")
        @Max(value = 20, message = "questionCount cannot exceed 20")
        Integer questionCount,

        @Schema(description = "Overrides the interview's focus skills for this run only")
        @Size(max = 20)
        List<@NotBlank @Size(max = 60) String> focusSkills,

        @Schema(
                description = "Replace existing AI questions (true) or append after them (false)",
                defaultValue = "true")
        Boolean replaceExisting) {

    public int questionCountOrDefault() {
        return questionCount == null ? 5 : questionCount;
    }

    public boolean replaceExistingOrDefault() {
        return replaceExisting == null || replaceExisting;
    }
}
