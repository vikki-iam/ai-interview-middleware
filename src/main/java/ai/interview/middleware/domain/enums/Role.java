package ai.interview.middleware.domain.enums;

/**
 * Platform roles. Spring Security authorities are these names prefixed with {@code ROLE_}, which is
 * what {@code hasRole(...)} expects.
 */
public enum Role {

    /** Full access, including candidate deletion and interview administration. */
    ADMIN,

    /** Manages own interviews, generates questions, submits results. */
    INTERVIEWER,

    /** Read-only access to own interview schedule. */
    CANDIDATE;

    public String authority() {
        return "ROLE_" + name();
    }
}
