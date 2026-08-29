package ai.interview.middleware.repository.spec;

import ai.interview.middleware.domain.entity.Interview;
import ai.interview.middleware.domain.enums.InterviewStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Criteria fragments for interview listing and filtering. */
public final class InterviewSpecifications {

    private InterviewSpecifications() {}

    public static Specification<Interview> unfiltered() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Joins the candidate so a recruiter can search by candidate name from the interview list.
     * {@code query.distinct} is not needed: both associations are many-to-one, so the join cannot
     * multiply rows.
     */
    public static Specification<Interview> matchesText(String term) {
        return (root, query, cb) -> {
            String pattern = "%" + term.toLowerCase() + "%";
            var candidate = root.join("candidate", JoinType.INNER);
            Predicate title = cb.like(cb.lower(root.get("title")), pattern);
            Predicate roleTitle = cb.like(cb.lower(root.get("roleTitle")), pattern);
            Predicate firstName = cb.like(cb.lower(candidate.get("firstName")), pattern);
            Predicate lastName = cb.like(cb.lower(candidate.get("lastName")), pattern);
            return cb.or(title, roleTitle, firstName, lastName);
        };
    }

    public static Specification<Interview> hasStatus(InterviewStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Interview> hasCandidate(UUID candidateId) {
        return (root, query, cb) -> cb.equal(root.get("candidate").get("id"), candidateId);
    }

    public static Specification<Interview> hasInterviewer(UUID interviewerId) {
        return (root, query, cb) -> cb.equal(root.get("interviewer").get("id"), interviewerId);
    }

    public static Specification<Interview> scheduledFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("scheduledAt"), from);
    }

    public static Specification<Interview> scheduledUntil(Instant until) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("scheduledAt"), until);
    }
}
