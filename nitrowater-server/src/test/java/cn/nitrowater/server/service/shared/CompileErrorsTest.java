package cn.nitrowater.server.service.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks {@link CompileErrors#oneLine}: it must surface the line that actually carries the error
 * (Python's trailing {@code SyntaxError: ...}, a C/Java {@code ...: error: ...}) rather than the
 * leading header line, while still stripping quoted paths and staying a single short line.
 */
class CompileErrorsTest {

    @Test
    void prefersPythonSyntaxErrorOverTheFileHeader() {
        String diagnostic = "  File \"/piston/jobs/abc/main.py\", line 2\n"
                + "    def foo(\n"
                + "          ^\n"
                + "SyntaxError: '(' was never closed\n";

        assertThat(CompileErrors.oneLine(diagnostic)).isEqualTo("SyntaxError: '(' was never closed");
    }

    @Test
    void prefersIndentationErrorOverTheFileHeader() {
        String diagnostic = "  File \"/piston/jobs/abc/main.py\", line 3\n"
                + "    return 1\n"
                + "IndentationError: unexpected indent\n";

        assertThat(CompileErrors.oneLine(diagnostic)).isEqualTo("IndentationError: unexpected indent");
    }

    @Test
    void keepsTheJavaErrorLine() {
        String diagnostic = "Main.java:2: error: ';' expected\n1 error\nerror: compilation failed\n";

        assertThat(CompileErrors.oneLine(diagnostic)).isEqualTo("Main.java:2: error: ';' expected");
    }

    @Test
    void skipsGccContextLineForTheActualError() {
        String diagnostic = "main.c: In function 'main':\n"
                + "main.c:6:18: error: 'x' undeclared (first use in this function)\n"
                + "compilation terminated.\n";

        assertThat(CompileErrors.oneLine(diagnostic))
                .isEqualTo("main.c:6:18: error: 'x' undeclared (first use in this function)");
    }

    @Test
    void stripsAQuotedPathWhenOnlyTheHeaderIsPresent() {
        String diagnostic = "File \"/piston/jobs/abc/main.py\", line 9\n";

        assertThat(CompileErrors.oneLine(diagnostic)).isEqualTo("File \"main.py\", line 9");
    }

    @Test
    void returnsNullForNullOrGenericFailure() {
        assertThat(CompileErrors.oneLine(null)).isNull();
        assertThat(CompileErrors.oneLine("error: compilation failed\n")).isNull();
    }

    @Test
    void truncatesToThreeHundredCharacters() {
        String diagnostic = "main.c:1:1: error: " + "x".repeat(400);

        assertThat(CompileErrors.oneLine(diagnostic)).hasSize(300);
    }
}
