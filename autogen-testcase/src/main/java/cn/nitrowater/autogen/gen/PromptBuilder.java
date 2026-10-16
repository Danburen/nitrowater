package cn.nitrowater.autogen.gen;

import cn.nitrowater.autogen.ast.ClassFact;
import cn.nitrowater.autogen.ast.MethodFact;
import java.util.List;
import java.util.StringJoiner;

/**
 * Turns AST facts into the two chat messages sent to the model.
 *
 * <p>Design note: the prompt carries <em>facts</em> (signature, modifiers,
 * thrown exceptions, complexity, javadoc) first and the verbatim method source
 * after, so the model can both reason about intent and see concrete behaviour.
 * The output contract (single JUnit 5 file, same package, deterministic name)
 * is stated explicitly to make the downstream compile check meaningful.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    public static String systemPrompt() {
        return """
                你是一名资深 Java 测试工程师，正在为「自动生成测试用例」工具撰写输出。

                任务：根据给定的 Java 类 AST 事实与源码，为指定方法生成 JUnit 5 (Jupiter) 测试。

                硬性要求：
                1. 只输出一个完整的 Java 源文件，不要任何解释文字。
                2. 包名必须与被测类完全一致；测试类命名为 <被测类名>Test。
                3. 使用 JUnit 5 注解（org.junit.jupiter.api.Test 等）与断言（org.junit.jupiter.api.Assertions.*）。
                4. 必须覆盖：正常路径、边界值（0/负数/空）、异常路径（用 assertThrows 断言具体异常类型）、以及 null/空集合（如适用）。
                5. 每个 @Test 只断言一个行为，测试方法命名要有描述性（如 add_negativeNumbers_returnsSum）。
                6. 只使用被测类的 public API 与 Java 标准库，保证代码可编译。
                7. 不要引入 Mockito 等额外依赖。
                """;
    }

    public static String userPrompt(ClassFact fact, List<MethodFact> targets) {
        StringBuilder sb = new StringBuilder();
        sb.append("【被测类】\n");
        sb.append("package: ").append(fact.packageName()).append('\n');
        sb.append("kind: ").append(fact.kind()).append('\n');
        sb.append("class: ").append(fact.className()).append('\n');
        if (!fact.classJavadoc().isBlank()) {
            sb.append("class javadoc: ").append(oneLine(fact.classJavadoc())).append('\n');
        }
        sb.append('\n');

        sb.append("【类的全部方法(供参考，可用作被测依赖)】\n");
        for (MethodFact m : fact.methods()) {
            sb.append("  - ").append(m.signature()).append('\n');
        }
        sb.append('\n');

        sb.append("【需要生成测试的目标方法】\n");
        for (MethodFact m : targets) {
            sb.append("方法: ").append(m.signature()).append('\n');
            sb.append("  - 返回类型: ").append(m.returnType()).append('\n');
            sb.append("  - 修饰符: ").append(String.join(" ", m.modifiers())).append('\n');
            sb.append("  - 参数: ").append(params(m)).append('\n');
            sb.append("  - 抛出异常: ")
                    .append(m.thrownExceptions().isEmpty() ? "(未声明，可能有运行时异常)" : String.join(", ", m.thrownExceptions()))
                    .append('\n');
            sb.append("  - 圈复杂度(粗略): ").append(m.complexity())
                    .append("，代码行数: ").append(m.lineCount()).append('\n');
            if (!m.javadoc().isBlank()) {
                sb.append("  - javadoc: ").append(oneLine(m.javadoc())).append('\n');
            }
            sb.append("  - 源码:\n");
            sb.append(indent(m.source(), "      ")).append('\n');
        }

        sb.append('\n').append("请现在输出 ").append(fact.className()).append("Test.java 的完整源码。");
        return sb.toString();
    }

    private static String params(MethodFact method) {
        if (method.parameters().isEmpty()) {
            return "(无)";
        }
        StringJoiner joiner = new StringJoiner(", ");
        method.parameters().forEach(p -> joiner.add(p.type() + " " + p.name()));
        return joiner.toString();
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static String indent(String text, String prefix) {
        return prefix + text.replace("\n", "\n" + prefix);
    }
}
