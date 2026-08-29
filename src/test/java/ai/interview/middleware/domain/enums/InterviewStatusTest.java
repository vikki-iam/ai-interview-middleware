package ai.interview.middleware.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The interview state machine.
 *
 * <p>Worth testing exhaustively despite being a small enum: it is the only thing preventing a
 * completed interview from being reopened and re-scored, and a wrong transition table would corrupt
 * the dashboard's completed/pending counts silently.
 */
class InterviewStatusTest {

    @Nested
    @DisplayName("legal transitions")
    class LegalTransitions {

        @ParameterizedTest(name = "{0} -> {1} is allowed")
        @CsvSource({
            "SCHEDULED, IN_PROGRESS",
            "SCHEDULED, COMPLETED",
            "SCHEDULED, CANCELLED",
            "IN_PROGRESS, COMPLETED",
            "IN_PROGRESS, CANCELLED"
        })
        void allowsForwardTransitions(InterviewStatus from, InterviewStatus to) {
            assertThat(from.canTransitionTo(to)).isTrue();
        }
    }

    @Nested
    @DisplayName("illegal transitions")
    class IllegalTransitions {

        @ParameterizedTest(name = "{0} -> {1} is rejected")
        @CsvSource({
            "IN_PROGRESS, SCHEDULED",
            "COMPLETED, SCHEDULED",
            "COMPLETED, IN_PROGRESS",
            "COMPLETED, CANCELLED",
            "CANCELLED, SCHEDULED",
            "CANCELLED, IN_PROGRESS",
            "CANCELLED, COMPLETED"
        })
        void rejectsBackwardsAndTerminalTransitions(InterviewStatus from, InterviewStatus to) {
            assertThat(from.canTransitionTo(to)).isFalse();
        }

        /**
         * Re-applying the current status is not a transition. The service treats it as a no-op before
         * consulting this method, so returning false here keeps "nothing changed" distinguishable
         * from "this move is legal".
         */
        @ParameterizedTest(name = "{0} -> {0} is not a transition")
        @EnumSource(InterviewStatus.class)
        void rejectsSelfTransitions(InterviewStatus status) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }

    @Test
    @DisplayName("terminal statuses accept no outgoing transition at all")
    void terminalStatusesAreFinal() {
        for (InterviewStatus target : InterviewStatus.values()) {
            assertThat(InterviewStatus.COMPLETED.canTransitionTo(target)).isFalse();
            assertThat(InterviewStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("isTerminal identifies exactly COMPLETED and CANCELLED")
    void identifiesTerminalStatuses() {
        assertThat(InterviewStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(InterviewStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(InterviewStatus.SCHEDULED.isTerminal()).isFalse();
        assertThat(InterviewStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    /**
     * The dashboard's "pending" figure is defined by this set. If a status were added without being
     * classified, it would silently vanish from both the pending and completed totals.
     */
    @Test
    @DisplayName("PENDING covers every non-terminal status")
    void pendingCoversEveryNonTerminalStatus() {
        assertThat(InterviewStatus.PENDING)
                .containsExactlyInAnyOrder(InterviewStatus.SCHEDULED, InterviewStatus.IN_PROGRESS);

        for (InterviewStatus status : InterviewStatus.values()) {
            assertThat(InterviewStatus.PENDING.contains(status)).isEqualTo(!status.isTerminal());
        }
    }
}
