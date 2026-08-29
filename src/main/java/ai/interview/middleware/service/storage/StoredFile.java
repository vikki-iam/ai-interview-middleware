package ai.interview.middleware.service.storage;

import ai.interview.middleware.domain.enums.StorageType;

/** The result of a successful store operation: what to persist alongside the resume metadata. */
public record StoredFile(
        String storageKey, long sizeBytes, String checksumSha256, StorageType storageType) {}
