package ai.interview.middleware.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of an interview. Mirrors {@code ck_interviews_status}. */
public enum InterviewStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /** Statuses counted as "pending" on the dashboard. */
    public static final Set<InterviewStatus> PENDING = EnumSet.of(SCHEDULED, IN_PROGRESS);

    /** Legal forward transitions; anything else is rejected with 400 rather than silently applied. */
    public boolean canTransitionTo(InterviewStatus target) {
        return switch (this) {
            case SCHEDULED -> target == IN_PROGRESS || target == COMPLETED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
