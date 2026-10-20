package cn.nitrowater.autogen.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CodeExtractorTest {

    @Test
    void stripsMarkdownFence() {
        String raw = "Here you go:\n```java\npackage demo;\nclass A {}\n```\ntrailing";
        String code = CodeExtractor.extractJava(raw);
        assertTrue(code.startsWith("package demo;"));
        assertTrue(code.endsWith("}"));
    }

    @Test
    void returnsPlainCodeUnchanged() {
        String raw = "package demo;\nclass A {}\n";
        assertEquals("package demo;\nclass A {}", CodeExtractor.extractJava(raw));
    }

    @Test
    void dropsLeadingProseWithoutFence() {
        String raw = "Sure! Here is the test:\npackage demo;\nclass A {}";
        assertEquals("package demo;\nclass A {}", CodeExtractor.extractJava(raw));
    }
}
