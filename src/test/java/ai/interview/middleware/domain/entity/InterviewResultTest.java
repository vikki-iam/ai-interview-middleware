package ai.interview.middleware.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Overall-score derivation.
 *
 * <p>The overall score is computed server-side rather than accepted from the client, so a scorecard
 * can never claim a headline number that contradicts its own breakdown. These tests pin the rounding
 * behaviour, which is the part that would otherwise drift unnoticed.
 */
class InterviewResultTest {

    private InterviewResult resultWith(String technical, String communication, String problemSolving) {
        InterviewResult result = new InterviewResult();
        result.setTechnicalScore(new BigDecimal(technical));
        result.setCommunicationScore(new BigDecimal(communication));
        result.setProblemSolvingScore(new BigDecimal(problemSolving));
        result.recalculateOverallScore();
        return result;
    }

    @ParameterizedTest(name = "({0} + {1} + {2}) / 3 = {3}")
    @CsvSource({
        "8.5, 8.0, 9.0, 8.5",
        "10.0, 10.0, 10.0, 10.0",
        "0.0, 0.0, 0.0, 0.0",
        "7.0, 7.5, 6.5, 7.0",
        // 22.0 / 3 = 7.333... -> 7.3
        "8.0, 7.0, 7.0, 7.3",
        // 23.0 / 3 = 7.666... -> 7.7
        "8.0, 8.0, 7.0, 7.7",
        // 2.5 / 3 = 0.833... -> 0.8
        "1.0, 1.0, 0.5, 0.8"
    })
    @DisplayName("averages the three dimensions to one decimal place")
    void averagesDimensionScores(
            String technical, String communication, String problemSolving, String expected) {

        InterviewResult result = resultWith(technical, communication, problemSolving);

        assertThat(result.getOverallScore()).isEqualByComparingTo(new BigDecimal(expected));
    }

    /**
     * HALF_UP, not banker's rounding: a 7.25 average becomes 7.3 rather than 7.2. Interviewers expect
     * the arithmetic they would do by hand.
     */
    @Test
    @DisplayName("rounds halves upward")
    void roundsHalvesUpward() {
        // 21.5 / 3 = 7.1666... -> 7.2
        assertThat(resultWith("7.5", "7.0", "7.0").getOverallScore())
                .isEqualByComparingTo(new BigDecimal("7.2"));
        // 22.5 / 3 = 7.5 exactly
        assertThat(resultWith("7.5", "7.5", "7.5").getOverallScore())
                .isEqualByComparingTo(new BigDecimal("7.5"));
    }

    @Test
    @DisplayName("always produces exactly one decimal place, matching numeric(4,1)")
    void alwaysScalesToOneDecimal() {
        assertThat(resultWith("9.0", "9.0", "9.0").getOverallScore().scale()).isEqualTo(1);
        assertThat(resultWith("0.0", "0.0", "0.0").getOverallScore().scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("recalculating after a correction replaces the previous value")
    void recalculationOverwritesPreviousValue() {
        InterviewResult result = resultWith("2.0", "2.0", "2.0");
        assertThat(result.getOverallScore()).isEqualByComparingTo(new BigDecimal("2.0"));

        result.setTechnicalScore(new BigDecimal("9.0"));
        result.setCommunicationScore(new BigDecimal("9.0"));
        result.setProblemSolvingScore(new BigDecimal("9.0"));
        result.recalculateOverallScore();

        assertThat(result.getOverallScore()).isEqualByComparingTo(new BigDecimal("9.0"));
    }

    @Test
    @DisplayName("the derived score stays inside the ck_interview_results_overall bounds")
    void staysWithinDatabaseBounds() {
        BigDecimal max = resultWith("10.0", "10.0", "10.0").getOverallScore();
        BigDecimal min = resultWith("0.0", "0.0", "0.0").getOverallScore();

        assertThat(max).isLessThanOrEqualTo(BigDecimal.TEN);
        assertThat(min).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
