package ai.interview.middleware.repository.projection;

/**
 * Read-only projection for {@code GROUP BY status} aggregations.
 *
 * <p>The enum is projected as its string name so the same projection serves candidate statuses,
 * interview statuses and recommendations without a type per aggregate.
 */
public interface StatusCount {

    String getName();

    long getTotal();
}
