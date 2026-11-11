package cn.nitrowater.server.service.ai.tool;

import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import cn.nitrowater.server.service.ai.model.tool.ToolResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Exact decimal arithmetic for the agent.
 *
 * <p>Language models are unreliable at multi-step decimal arithmetic: a probe of ten realistic
 * questions produced three wrong values (binary rounding on {@code 0.1 + 0.2}, a 75-unit error on
 * a large multiplication, a 10-unit error on a percentage). Handing those to a deterministic
 * evaluator is both cheaper and more accurate than a code sandbox, and the result stays checkable
 * by the user.</p>
 *
 * <p>The grammar is deliberately tiny — no variables, no exponent operator, no percent sign — so
 * the tool can never become an execution surface. Anything that needs a real runtime belongs to
 * {@code runCode}.</p>
 */
@Component
public class CalculateTool {

    /** What the model reads; procedure lives here so the prompt only carries routing. */
    public static final String DESCRIPTION = """
            精确计算一个数学表达式。问题里出现具体算式（小数、多步乘除、括号、百分比换算后的乘除）时先调用，不要心算，也不要用代码执行工具代替。
            支持：数字（含小数）、+ - * /、括号、一元负号，函数 round(x[, n]) / abs(x) / min(...) / max(...) / sqrt(x)。不支持变量、幂运算符和百分号，百分比先换算成小数再写。
            返回 data.result（十进制字符串）。除数为 0 或负数开方返回 tool.math_error，语法错误返回 tool.invalid_request。
            """;

    /** Longest expression accepted; far above any hand-written question, far below a denial-of-service. */
    private static final int MAX_EXPRESSION_CHARS = 400;

    /** Nesting bound: the parser is recursive, so depth must be bounded independently of length. */
    private static final int MAX_DEPTH = 32;

    /** Division and rounding never produce binary noise; sixteen places cover money and ratios. */
    private static final int DIVISION_SCALE = 16;

    /** Guard against a long chain of multiplications turning into a page of digits. */
    private static final int MAX_RESULT_CHARS = 120;

    /**
     * Evaluates one expression.
     *
     * @param expression arithmetic expression, e.g. {@code (12345.678 * 987.65) / 3}
     * @param context    Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON envelope with {@code data.result}, or a normalised failure
     */
    @Tool(name = "calculate", description = DESCRIPTION)
    public String calculate(
            @ToolParam(description = "要计算的算式，只含数字、+ - * /、括号与支持的函数") String expression,
            ToolContext context) {
        AgentTrace trace = trace(context);
        String argument = StringUtil.isBlank(expression) ? null : expression.trim();
        if (StringUtil.isBlank(argument)) {
            return failure(trace, ToolCode.INVALID_REQUEST, "算式为空，请给出要计算的表达式。", argument);
        }
        if (argument.length() > MAX_EXPRESSION_CHARS) {
            return failure(trace, ToolCode.INVALID_REQUEST,
                    "算式过长，请拆成几个较短的算式分别计算。", argument);
        }
        String source = normalize(argument);
        if (source.indexOf('%') >= 0) {
            return failure(trace, ToolCode.INVALID_REQUEST,
                    "不支持百分号，请先把百分比换算成小数再计算。", argument);
        }

        BigDecimal value;
        String result;
        try {
            value = new Parser(source).parse();
            result = format(value);
        } catch (MathFailure ex) {
            return failure(trace, ToolCode.MATH_ERROR, ex.getMessage(), argument);
        } catch (ExpressionException | ArithmeticException ex) {
            return failure(trace, ToolCode.INVALID_REQUEST, "算式无法解析：" + ex.getMessage(), argument);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expression", source);
        data.put("result", result);
        return ToolResponse.ok(data)
                .record(trace, "calculate", argument, 1)
                .json();
    }

    /**
     * Folds the symbols people actually type into the operators the grammar accepts: a Chinese
     * question writes {@code ×} and {@code ÷}, and the model often copies them verbatim.
     *
     * @param expression raw expression
     * @return the same expression with full-width digits and operators normalised
     */
    private static String normalize(String expression) {
        StringBuilder normalized = new StringBuilder(expression.length());
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            normalized.append(switch (value) {
                case '×', '✕', '✖', '＊' -> '*';
                case '÷', '／' -> '/';
                case '−', '–', '—', '－' -> '-';
                case '＋' -> '+';
                case '（' -> '(';
                case '）' -> ')';
                case '，', '、' -> ',';
                case '％' -> '%';
                default -> value >= '０' && value <= '９'
                        ? (char) ('0' + (value - '０'))
                        : value;
            });
        }
        return normalized.toString();
    }

    private static String failure(AgentTrace trace, ToolCode code, String message, String argument) {
        return ToolResponse.of(code, message)
                .record(trace, "calculate", argument, 0)
                .json();
    }

    /**
     * @param value exact result
     * @return plain decimal string without trailing zeros, or a readable failure for absurd values
     */
    private static String format(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        String plain = (trimmed.scale() < 0 ? trimmed.setScale(0) : trimmed).toPlainString();
        if (plain.length() > MAX_RESULT_CHARS) {
            throw new ExpressionException("结果位数过多，请缩小数值范围或分步计算");
        }
        return plain;
    }

    /**
     * Recursive-descent evaluator over the fixed grammar. Recursion is bounded by
     * {@link #MAX_DEPTH} at every nesting point, so a pathological input cannot exhaust the stack.
     */
    private static final class Parser {

        private final String source;
        private int index;
        private int depth;

        Parser(String source) {
            this.source = source;
        }

        BigDecimal parse() {
            BigDecimal value = expression();
            skipSpaces();
            if (index < source.length()) {
                throw new ExpressionException("第 " + (index + 1) + " 个字符附近有多余内容");
            }
            return value;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    value = value.add(term());
                } else if (match('-')) {
                    value = value.subtract(term());
                } else {
                    return value;
                }
            }
        }

        private BigDecimal term() {
            BigDecimal value = unary();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    value = value.multiply(unary());
                } else if (match('/')) {
                    BigDecimal divisor = unary();
                    if (divisor.signum() == 0) {
                        throw new MathFailure("除数为 0");
                    }
                    value = value.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal unary() {
            skipSpaces();
            if (match('-')) {
                return unary().negate();
            }
            if (match('+')) {
                return unary();
            }
            return primary();
        }

        private BigDecimal primary() {
            skipSpaces();
            if (match('(')) {
                return nested(() -> {
                    BigDecimal value = expression();
                    skipSpaces();
                    if (!match(')')) {
                        throw new ExpressionException("缺少右括号");
                    }
                    return value;
                });
            }
            skipSpaces();
            if (index < source.length() && Character.isLetter(source.charAt(index))) {
                return function();
            }
            return number();
        }

        private BigDecimal function() {
            String name = identifier();
            skipSpaces();
            if (!match('(')) {
                throw new ExpressionException("函数 " + name + " 后需要括号");
            }
            return nested(() -> {
                BigDecimal value = apply(name);
                skipSpaces();
                if (!match(')')) {
                    throw new ExpressionException("函数 " + name + " 缺少右括号");
                }
                return value;
            });
        }

        private BigDecimal apply(String name) {
            switch (name) {
                case "abs" -> {
                    return single(name).abs();
                }
                case "sqrt" -> {
                    BigDecimal value = single(name);
                    if (value.signum() < 0) {
                        throw new MathFailure("负数不能开平方");
                    }
                    return value.sqrt(MathContext.DECIMAL128);
                }
                case "round" -> {
                    return round();
                }
                case "min" -> {
                    return extremum(name, true);
                }
                case "max" -> {
                    return extremum(name, false);
                }
                default -> throw new ExpressionException("不支持的函数：" + name);
            }
        }

        private BigDecimal single(String name) {
            BigDecimal value = expression();
            skipSpaces();
            if (match(',')) {
                throw new ExpressionException("函数 " + name + " 只接受一个参数");
            }
            return value;
        }

        private BigDecimal round() {
            BigDecimal value = expression();
            skipSpaces();
            int digits = 0;
            if (match(',')) {
                BigDecimal places = expression();
                try {
                    digits = places.intValueExact();
                } catch (ArithmeticException ex) {
                    throw new ExpressionException("round 的小数位数必须是整数");
                }
                if (digits < 0 || digits > DIVISION_SCALE) {
                    throw new ExpressionException("round 的小数位数需在 0 到 " + DIVISION_SCALE + " 之间");
                }
                skipSpaces();
                if (match(',')) {
                    throw new ExpressionException("函数 round 只接受一个或两个参数");
                }
            }
            return value.setScale(digits, RoundingMode.HALF_UP);
        }

        private BigDecimal extremum(String name, boolean min) {
            BigDecimal best = expression();
            int count = 1;
            while (true) {
                skipSpaces();
                if (!match(',')) {
                    break;
                }
                BigDecimal next = expression();
                count++;
                best = (min ? best.min(next) : best.max(next));
            }
            if (count < 2) {
                throw new ExpressionException("函数 " + name + " 至少需要两个参数");
            }
            return best;
        }

        private BigDecimal number() {
            int start = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            if (start == index) {
                throw new ExpressionException(index >= source.length()
                        ? "算式不完整"
                        : "无法识别第 " + (index + 1) + " 个字符：" + source.charAt(index));
            }
            try {
                return new BigDecimal(source.substring(start, index));
            } catch (NumberFormatException ex) {
                throw new ExpressionException("无法识别数字：" + source.substring(start, index));
            }
        }

        private String identifier() {
            int start = index;
            while (index < source.length() && Character.isLetter(source.charAt(index))) {
                index++;
            }
            return source.substring(start, index).toLowerCase(Locale.ROOT);
        }

        private BigDecimal nested(Supplier<BigDecimal> body) {
            if (++depth > MAX_DEPTH) {
                throw new ExpressionException("括号层级过深");
            }
            try {
                return body.get();
            } finally {
                depth--;
            }
        }

        private void skipSpaces() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean match(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }
    }

    /** Syntax or bound violation: the model can fix the expression and retry. */
    private static final class ExpressionException extends RuntimeException {

        private ExpressionException(String message) {
            super(message);
        }
    }

    /** Mathematically undefined result: a corrected expression or an explanation is needed. */
    private static final class MathFailure extends RuntimeException {

        private MathFailure(String message) {
            super(message);
        }
    }

    private static AgentTrace trace(ToolContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
        return value instanceof AgentTrace agentTrace ? agentTrace : null;
    }
}
