package cn.nitrowater.core.web.legal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link LegalDocuments}.
 *
 * <p>Both the BFF and the account service serve these files publicly and read them from a
 * directory that an operator writes to, so the whitelist is the security boundary: the requested
 * name is never joined into a path before it is known to be one of two literals.</p>
 */
class LegalDocumentsTest {

    @TempDir
    Path dir;

    @Test
    void readsWhitelistedDocumentAsUtf8() throws IOException {
        Files.writeString(dir.resolve("privacy.txt"), "隐私政策正文", StandardCharsets.UTF_8);

        assertEquals("隐私政策正文", LegalDocuments.read(dir, LegalDocuments.PRIVACY));
    }

    @Test
    void rejectsNamesOutsideTheWhitelistBeforeTouchingTheDisk() {
        assertFalse(LegalDocuments.isSupported("cookie-policy"));
        assertFalse(LegalDocuments.isSupported(null));
        assertNull(LegalDocuments.fileOf(dir, "../privacy"));
        assertNull(LegalDocuments.fileOf(dir, "privacy/../../etc/passwd"));
        assertNull(LegalDocuments.fileOf(dir, "terms.txt"));
    }

    @Test
    void missingFileReadsAsNullInsteadOfThrowing() {
        assertNull(LegalDocuments.fileOf(dir, LegalDocuments.TERMS));
        assertTrue(LegalDocuments.NAMES.contains(LegalDocuments.TERMS));
    }

    @Test
    void configuredDirectoryWinsOverAutoDetection() {
        Path configured = dir.resolve("custom");

        assertEquals(configured.toAbsolutePath().normalize(),
                LegalDocuments.resolveDirectory(configured.toString()));
    }

    @Test
    void blankConfigurationFallsBackToAnAbsoluteCandidate() {
        Path resolved = LegalDocuments.resolveDirectory("  ");

        assertTrue(resolved.isAbsolute(),
                "a blank config must still yield a usable path, got: " + resolved);
    }
}
