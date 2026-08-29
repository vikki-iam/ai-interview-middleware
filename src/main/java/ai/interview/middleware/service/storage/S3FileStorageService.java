package ai.interview.middleware.service.storage;

import ai.interview.middleware.config.AppProperties;
import ai.interview.middleware.domain.enums.StorageType;
import ai.interview.middleware.exception.ResourceNotFoundException;
import ai.interview.middleware.exception.StorageException;
import ai.interview.middleware.exception.SecretResolutionException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * S3 storage for production.
 *
 * <p>Selected when {@code app.storage.type=s3}. The client is built with the default credential
 * provider chain, so on EKS the pod's IRSA role grants the object permissions and no access key
 * exists anywhere in the deployment.
 *
 * <p>Objects are written with SSE-S3 and a checksum, and the original filename is preserved as object
 * metadata so the bucket remains useful if the database is ever rebuilt from it.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

    private final S3Client s3Client;
    private final String bucket;
    private final String prefix;

    public S3FileStorageService(S3Client s3Client, AppProperties properties) {
        AppProperties.S3Storage config = properties.storage().s3();
        if (!StringUtils.hasText(config.bucket())) {
            throw new SecretResolutionException(
                    "app.storage.s3.bucket is required when app.storage.type=s3 "
                            + "(set APP_STORAGE_S3_BUCKET)");
        }
        this.s3Client = s3Client;
        this.bucket = config.bucket();
        this.prefix = normalisePrefix(config.prefix());
        log.info("S3 resume storage configured: bucket={} prefix='{}'", bucket, prefix);
    }

    @Override
    public StorageType storageType() {
        return StorageType.S3;
    }

    @Override
    public StoredFile store(
            UUID candidateId, String originalFilename, String contentType, byte[] content) {
        String key = StorageKeyFactory.buildKey(candidateId, originalFilename);
        String checksum = StorageKeyFactory.sha256Hex(content);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(key))
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .metadata(
                                    Map.of(
                                            "candidate-id", candidateId.toString(),
                                            "original-filename", StorageKeyFactory.sanitise(originalFilename),
                                            "sha256", checksum))
                            .build(),
                    RequestBody.fromBytes(content));
            log.debug("Stored {} bytes at s3://{}/{}", content.length, bucket, objectKey(key));
            return new StoredFile(key, content.length, checksum, StorageType.S3);
        } catch (S3Exception e) {
            throw new StorageException(
                    "Failed to upload resume to s3://%s/%s (%s)"
                            .formatted(bucket, objectKey(key), e.awsErrorDetails().errorCode()),
                    e);
        }
    }

    @Override
    public byte[] retrieve(String storageKey) {
        try {
            return s3Client
                    .getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(objectKey(storageKey)).build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("Stored file", storageKey);
        } catch (S3Exception e) {
            throw new StorageException(
                    "Failed to download resume from s3://%s/%s (%s)"
                            .formatted(bucket, objectKey(storageKey), e.awsErrorDetails().errorCode()),
                    e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            // S3 DeleteObject is already idempotent: a missing key returns 204.
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(objectKey(storageKey)).build());
            log.debug("Deleted s3://{}/{}", bucket, objectKey(storageKey));
        } catch (S3Exception e) {
            throw new StorageException(
                    "Failed to delete resume at s3://%s/%s (%s)"
                            .formatted(bucket, objectKey(storageKey), e.awsErrorDetails().errorCode()),
                    e);
        }
    }

    private String objectKey(String storageKey) {
        return prefix.isEmpty() ? storageKey : prefix + "/" + storageKey;
    }

    private String normalisePrefix(String configured) {
        if (!StringUtils.hasText(configured)) {
            return "";
        }
        return configured.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
