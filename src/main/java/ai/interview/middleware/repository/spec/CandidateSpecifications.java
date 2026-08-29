package ai.interview.middleware.repository.spec;

import ai.interview.middleware.domain.entity.Candidate;
import ai.interview.middleware.domain.enums.CandidateStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria fragments for candidate search.
 *
 * <p>Specifications rather than a single JPQL query with {@code :param IS NULL} guards: the
 * generated SQL contains only the filters the caller actually supplied, so PostgreSQL can use
 * {@code idx_candidates_status} instead of falling back to a sequential scan.
 */
public final class CandidateSpecifications {

    private CandidateSpecifications() {}

    /** Matches nothing extra; a neutral element for composing optional filters. */
    public static Specification<Candidate> unfiltered() {
        return (root, query, cb) -> cb.conjunction();
    }

    /** Case-insensitive contains-match across the fields a recruiter would type into one box. */
    public static Specification<Candidate> matchesText(String term) {
        return (root, query, cb) -> {
            String pattern = "%" + term.toLowerCase() + "%";
            Predicate firstName = cb.like(cb.lower(root.get("firstName")), pattern);
            Predicate lastName = cb.like(cb.lower(root.get("lastName")), pattern);
            Predicate email = cb.like(cb.lower(root.get("email")), pattern);
            Predicate skill = cb.like(cb.lower(root.get("primarySkill")), pattern);
            Predicate company = cb.like(cb.lower(cb.coalesce(root.get("currentCompany"), "")), pattern);
            return cb.or(firstName, lastName, email, skill, company);
        };
    }

    public static Specification<Candidate> hasStatus(CandidateStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Candidate> hasPrimarySkill(String skill) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("primarySkill")), skill.toLowerCase());
    }

    public static Specification<Candidate> hasMinimumExperience(java.math.BigDecimal years) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("yearsOfExperience"), years);
    }
}
