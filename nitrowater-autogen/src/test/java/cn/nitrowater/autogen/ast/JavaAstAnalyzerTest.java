package cn.nitrowater.autogen.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JavaAstAnalyzerTest {

    @Test
    void extractsClassAndMethodFacts() throws Exception {
        String src = """
                package demo;
                import java.util.List;
                public class Sample {
                    /** Integer division. */
                    public int divide(int a, int b) {
                        if (b == 0) {
                            throw new ArithmeticException("division by zero");
                        }
                        return a / b;
                    }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        Path file = Files.createTempFile("Sample", ".java");
        Files.writeString(file, src, StandardCharsets.UTF_8);

        ClassFact fact = new JavaAstAnalyzer().analyze(file);

        assertEquals("demo", fact.packageName());
        assertEquals("Sample", fact.className());
        assertEquals("class", fact.kind());
        assertEquals(2, fact.methods().size());
        assertTrue(fact.imports().contains("java.util.List"));

        MethodFact divide = fact.methods().stream()
                .filter(m -> m.name().equals("divide"))
                .findFirst()
                .orElseThrow();
        assertEquals("int", divide.returnType());
        assertEquals(2, divide.parameters().size());
        assertTrue(divide.complexity() >= 2, "if-branch should raise complexity above 1");
        assertTrue(divide.source().contains("ArithmeticException"));
    }
}
