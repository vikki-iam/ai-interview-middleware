package ai.interview.middleware.domain.enums;

/** Position of a candidate in the hiring pipeline. Mirrors {@code ck_candidates_status}. */
public enum CandidateStatus {
    NEW,
    SCREENING,
    INTERVIEWING,
    OFFERED,
    HIRED,
    REJECTED,
    ON_HOLD
}
