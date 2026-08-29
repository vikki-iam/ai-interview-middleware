package ai.interview.middleware.service.storage;

import ai.interview.middleware.domain.enums.StorageType;
import java.util.UUID;

/**
 * Abstraction over resume byte storage.
 *
 * <p>Local disk in development, S3 in production, selected by {@code app.storage.type}. The interface
 * is intentionally key-based rather than path-based so the S3 implementation is not forced to pretend
 * to be a filesystem.
 *
 * <p>Content is passed as a byte array rather than a stream because uploads are capped at
 * {@code app.storage.max-file-size-bytes} (10 MB by default) and the checksum has to be computed over
 * the whole payload anyway. Raising that cap significantly would mean revisiting this signature.
 */
public interface FileStorageService {

    /** Which backend this implementation writes to; persisted on the resume row. */
    StorageType storageType();

    /**
     * Stores the bytes and returns the locator to persist.
     *
     * @throws ai.interview.middleware.exception.StorageException if the write fails
     */
    StoredFile store(UUID candidateId, String originalFilename, String contentType, byte[] content);

    /**
     * @throws ai.interview.middleware.exception.ResourceNotFoundException if the key does not exist
     * @throws ai.interview.middleware.exception.StorageException if the read fails
     */
    byte[] retrieve(String storageKey);

    /** Idempotent: deleting an already-absent key is not an error. */
    void delete(String storageKey);
}
