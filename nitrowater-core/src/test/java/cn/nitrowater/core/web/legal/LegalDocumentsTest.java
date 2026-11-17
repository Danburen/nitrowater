package cn.nitrowater.core.web.legal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link LegalDocuments}.
 *
 * <p>An operator writes the mounted directory, so two properties matter: a requested key never
 * becomes a path (no traversal), and the index is derived from disk (a new document needs no code).</p>
 */
class LegalDocumentsTest {

    @TempDir
    Path dir;

    @Test
    void titlesEachDocumentByItsFirstLine() throws IOException {
        write("privacy.txt", "隐私政策\n\n隐私政策正文");
        write("terms.txt", "用户协议\n\n用户协议正文");
        write("cookies.txt", "Cookie 说明\n\nCookie 正文");

        List<LegalDocumentSummary> documents = new LegalDocuments(dir.toString()).index();
        Map<String, String> titles = documents.stream()
                .collect(Collectors.toMap(LegalDocumentSummary::key, LegalDocumentSummary::title));

        assertEquals(Set.of("privacy", "terms", "cookies"), titles.keySet());
        assertEquals("隐私政策", titles.get("privacy"));
        assertEquals("用户协议", titles.get("terms"));
        // A document nobody anticipated is titled by its own heading, not by a code-side map.
        assertEquals("Cookie 说明", titles.get("cookies"));
        assertTrue(documents.getFirst().updatedAt().isAfter(java.time.Instant.EPOCH));
    }

    @Test
    void skipsBlankLinesBeforeTheHeading() throws IOException {
        write("terms.txt", "\n\n  \n用户协议（2026 版）\n\n正文");

        assertEquals("用户协议（2026 版）", new LegalDocuments(dir.toString()).index().getFirst().title());
    }

    @Test
    void readsTheHeadingThroughAByteOrderMark() throws IOException {
        write("terms.txt", "\uFEFF用户协议\n\n正文");

        assertEquals("用户协议", new LegalDocuments(dir.toString()).index().getFirst().title());
        assertEquals("正文", new LegalDocuments(dir.toString()).read("terms"));
    }

    @Test
    void fallsBackToTheKeyWhenTheFirstLineIsAParagraph() throws IOException {
        write("refund.txt", "退".repeat(41));

        assertEquals("refund", new LegalDocuments(dir.toString()).index().getFirst().title());
    }

    @Test
    void doesNotUseAControlCharacterLineAsTheLabel() throws IOException {
        // U+202E (RTL override) would make a link read as something it is not.
        write("refund.txt", "\u202Egnp.txt 用户协议");

        assertEquals("refund", new LegalDocuments(dir.toString()).index().getFirst().title());
    }

    @Test
    void refusesToPublishSymlinks() throws IOException {
        Path secret = dir.resolve("elsewhere").resolve("secret.txt");
        Files.createDirectories(secret.getParent());
        Files.writeString(secret, "/etc/passwd 的内容", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(dir.resolve("terms.txt"), secret);
        } catch (UnsupportedOperationException | IOException ex) {
            // Windows without developer mode cannot create symlinks; nothing to assert there.
            return;
        }

        assertTrue(new LegalDocuments(dir.toString()).index().isEmpty());
        assertNull(new LegalDocuments(dir.toString()).read("terms"));
    }

    @Test
    void ignoresFilesThatAreNotDocuments() throws IOException {
        write("notes.md", "# 不是法务文件");
        write("Privacy.txt", "大写 key");
        write("child_safety.txt", "下划线");
        write(".txt", "空 key");

        assertTrue(new LegalDocuments(dir.toString()).index().isEmpty());
        assertNull(new LegalDocuments(dir.toString()).read("notes"));
        assertNull(new LegalDocuments(dir.toString()).read("child_safety"));
    }

    @Test
    void rejectsMalformedKeysBeforeTouchingTheDisk() throws IOException {
        write("privacy.txt", "隐私政策正文");

        assertNull(new LegalDocuments(dir.toString()).read("../privacy"));
        assertNull(new LegalDocuments(dir.toString()).read("privacy/../../etc/passwd"));
        assertNull(new LegalDocuments(dir.toString()).read("privacy.txt"));
        assertNull(new LegalDocuments(dir.toString()).read("Privacy"));
        assertNull(new LegalDocuments(dir.toString()).read(null));
    }

    @Test
    void readsThePublishedBodyWithoutItsTitleLine() throws IOException {
        write("terms.txt", "用户协议\n\n第一段\n第二段\n");

        assertEquals("第一段\n第二段\n", new LegalDocuments(dir.toString()).read("terms"));
        assertNull(new LegalDocuments(dir.toString()).read("privacy"));
    }

    @Test
    void keepsTheBodyIntactWhenTheFirstLineIsNotAHeading() throws IOException {
        // No heading: the page falls back to the key as title, and the paragraph stays in the body.
        String paragraph = "退".repeat(41);
        write("refund.txt", paragraph + "\n\n第二段");

        assertEquals(paragraph + "\n\n第二段", new LegalDocuments(dir.toString()).read("refund"));
    }

    @Test
    void missingDirectoryListsNothing() throws IOException {
        Path missing = dir.resolve("not-uploaded");

        assertTrue(new LegalDocuments(missing.toString()).index().isEmpty());
        assertNull(new LegalDocuments(missing.toString()).read("terms"));
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

    private void write(String name, String text) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
