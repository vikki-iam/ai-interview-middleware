package ai.interview.middleware.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Filename sanitisation and key construction.
 *
 * <p>This is the boundary where an attacker-controlled string (the uploaded filename) becomes a
 * filesystem path or an S3 key, so the traversal and hidden-file cases are the point of the class.
 */
class StorageKeyFactoryTest {

    @ParameterizedTest(name = "\"{0}\" is sanitised to \"{1}\"")
    @CsvSource({
        "resume.pdf, resume.pdf",
        "Resume.PDF, resume.pdf",
        "my resume.pdf, my_resume.pdf",
        "my   resume.pdf, my_resume.pdf",
        "resume(final).pdf, resume_final_.pdf"
    })
    @DisplayName("normalises case and collapses unsafe characters")
    void sanitisesOrdinaryNames(String input, String expected) {
        assertThat(StorageKeyFactory.sanitise(input)).isEqualTo(expected);
    }

    /**
     * Written with escapes rather than literal accented characters so the assertion cannot depend on
     * the encoding this source file happens to be compiled with.
     */
    @Test
    @DisplayName("replaces non-ASCII characters rather than passing them through")
    void replacesNonAsciiCharacters() {
        String accented = "résumé.pdf";

        assertThat(StorageKeyFactory.sanitise(accented)).isEqualTo("r_sum_.pdf");
    }

    /**
     * A traversal attempt must not survive sanitisation. Even though the local backend re-checks the
     * resolved path against its root, defence starts here.
     */
    @ParameterizedTest(name = "traversal attempt \"{0}\" is neutralised")
    @ValueSource(
            strings = {
                "../../../etc/passwd",
                "..\\..\\windows\\system32\\config\\sam",
                "/etc/shadow",
                "C:\\Windows\\win.ini",
                "....//....//etc/passwd"
            })
    @DisplayName("strips path separators and traversal sequences")
    void neutralisesPathTraversal(String hostileName) {
        String sanitised = StorageKeyFactory.sanitise(hostileName);

        assertThat(sanitised).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
        assertThat(sanitised).isNotEqualTo(".").isNotEqualTo("..");
    }

    @ParameterizedTest(name = "\"{0}\" cannot become a hidden file")
    @ValueSource(strings = {".", "..", ".bashrc", "...", ".....hidden"})
    @DisplayName("never produces a name starting with a dot")
    void neverProducesHiddenFiles(String input) {
        assertThat(StorageKeyFactory.sanitise(input)).doesNotStartWith(".");
    }

    @Test
    @DisplayName("falls back to a default when nothing usable remains")
    void fallsBackForEmptyResults() {
        assertThat(StorageKeyFactory.sanitise(null)).isEqualTo("resume");
        assertThat(StorageKeyFactory.sanitise("")).isEqualTo("resume");
        assertThat(StorageKeyFactory.sanitise("   ")).isEqualTo("resume");
        assertThat(StorageKeyFactory.sanitise("...")).isEqualTo("resume");
    }

    @Test
    @DisplayName("bounds the filename length")
    void boundsFilenameLength() {
        String overlong = "a".repeat(500) + ".pdf";

        assertThat(StorageKeyFactory.sanitise(overlong)).hasSize(100);
    }

    @Test
    @DisplayName("keys are scoped to the candidate and never collide")
    void keysAreScopedAndUnique() {
        UUID candidateId = UUID.randomUUID();

        String first = StorageKeyFactory.buildKey(candidateId, "resume.pdf");
        String second = StorageKeyFactory.buildKey(candidateId, "resume.pdf");

        assertThat(first).startsWith("candidates/" + candidateId + "/").endsWith("-resume.pdf");
        // Two uploads of the same filename must not overwrite each other.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("a hostile filename cannot escape the candidate prefix")
    void hostileFilenameStaysUnderPrefix() {
        UUID candidateId = UUID.randomUUID();

        String key = StorageKeyFactory.buildKey(candidateId, "../../evil.sh");

        assertThat(key).startsWith("candidates/" + candidateId + "/");
        // Exactly two separators: the two the template itself introduces.
        assertThat(key.chars().filter(character -> character == '/').count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("checksum matches the known SHA-256 of the input")
    void computesKnownSha256() {
        // Published SHA-256 of "abc"; a wrong implementation cannot accidentally match it.
        assertThat(StorageKeyFactory.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("checksum is stable and distinguishes different content")
    void checksumIsStableAndDiscriminating() {
        byte[] content = "interview".getBytes(StandardCharsets.UTF_8);

        assertThat(StorageKeyFactory.sha256Hex(content))
                .isEqualTo(StorageKeyFactory.sha256Hex(content))
                .hasSize(64)
                .isNotEqualTo(StorageKeyFactory.sha256Hex("interviews".getBytes(StandardCharsets.UTF_8)));
    }
}
