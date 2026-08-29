package ai.interview.middleware.domain.enums;

/**
 * Which storage backend produced a resume's {@code storage_key}. Persisted per row so an existing
 * file remains retrievable after the platform is migrated from local disk to S3.
 */
public enum StorageType {
    LOCAL,
    S3
}
