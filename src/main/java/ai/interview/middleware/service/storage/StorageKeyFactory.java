package ai.interview.middleware.service.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds storage keys and checksums.
 *
 * <p>Shared by both storage backends so a resume uploaded to local disk and one uploaded to S3 have
 * the same key shape, which is what makes a later local-to-S3 migration a copy rather than a rewrite
 * of the {@code resumes} table.
 */
public final class StorageKeyFactory {

    private static final int MAX_FILENAME_LENGTH = 100;

    private StorageKeyFactory() {}

    /**
     * {@code candidates/{candidateId}/{uuid}-{sanitised-filename}}.
     *
     * <p>The random UUID prefix means two uploads of {@code resume.pdf} never collide, and the
     * sanitisation strips anything that could escape the prefix ({@code ..}, separators, control
     * characters) so a hostile filename cannot become a path traversal on the local backend or an
     * unexpected prefix in S3.
     */
    public static String buildKey(UUID candidateId, String originalFilename) {
        return "candidates/%s/%s-%s".formatted(candidateId, UUID.randomUUID(), sanitise(originalFilename));
    }

    public static String sanitise(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resume";
        }
        // Take the last segment first: browsers on some platforms submit a full path.
        String base = filename.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        String cleaned =
                base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_").replaceAll("_{2,}", "_");
        // Strip leading dots so the result can never be "." , ".." or a hidden file.
        cleaned = cleaned.replaceAll("^\\.+", "");
        if (cleaned.isBlank()) {
            cleaned = "resume";
        }
        return cleaned.length() > MAX_FILENAME_LENGTH ? cleaned.substring(0, MAX_FILENAME_LENGTH) : cleaned;
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK; this cannot happen on a conforming runtime.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
