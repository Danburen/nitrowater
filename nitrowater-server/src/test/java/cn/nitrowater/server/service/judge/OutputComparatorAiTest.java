package cn.nitrowater.server.service.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complex pure-logic tests for {@link OutputComparator}: normalisation, four strategies,
 * structural JSON equality, and strategy-name fallback.
 *
 * <p>This is a demo for the "complex business logic" unit-test slot; it uses no Mock and
 * no Spring context.</p>
 */
class OutputComparatorAiTest {

    @Test
    void exactIgnoresCrLfAndTrailingWhitespace() {
        assertThat(OutputComparator.matches("a\r\nb  ", "a\nb", OutputComparator.Strategy.EXACT)).isTrue();
        assertThat(OutputComparator.matches("a\rb", "a\nb", OutputComparator.Strategy.EXACT)).isTrue();
    }

    @Test
    void exactStillDetectsDifferentContent() {
        assertThat(OutputComparator.matches("a b", "a  b", OutputComparator.Strategy.EXACT)).isFalse();
        assertThat(OutputComparator.matches("1", "2", OutputComparator.Strategy.EXACT)).isFalse();
    }

    @Test
    void nullAndEmptyAreEquivalentForNonJsonStrategies() {
        assertThat(OutputComparator.matches(null, "", OutputComparator.Strategy.EXACT)).isTrue();
        assertThat(OutputComparator.matches(null, "  ", OutputComparator.Strategy.TOKEN)).isTrue();
        assertThat(OutputComparator.matches(null, "", OutputComparator.Strategy.LINES)).isTrue();
    }

    @Test
    void tokenStrategyIgnoresWhitespaceShape() {
        assertThat(OutputComparator.matches("a  b\nc", "a b c", OutputComparator.Strategy.TOKEN)).isTrue();
        assertThat(OutputComparator.matches("a b", "a c", OutputComparator.Strategy.TOKEN)).isFalse();
    }

    @Test
    void tokenStrategyTreatsBlankAsEmptyTokenList() {
        assertThat(OutputComparator.matches("   ", "\n\t", OutputComparator.Strategy.TOKEN)).isTrue();
    }

    @Test
    void linesStrategyTrimsEachLine() {
        assertThat(OutputComparator.matches(" a \n b ", "a\nb", OutputComparator.Strategy.LINES)).isTrue();
        assertThat(OutputComparator.matches("a\nb", "b\na", OutputComparator.Strategy.LINES)).isFalse();
    }

    @Test
    void jsonIgnoresObjectKeyOrder() {
        assertThat(OutputComparator.matches(
                "{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}", OutputComparator.Strategy.JSON)).isTrue();
    }

    @Test
    void jsonComparesNestedStructuresAndNumbersWithinEpsilon() {
        assertThat(OutputComparator.matches(
                "{\"rows\":[{\"x\":1.0000005}],\"ok\":true}",
                "{\"ok\":true,\"rows\":[{\"x\":1.0}]}",
                OutputComparator.Strategy.JSON)).isTrue();
        assertThat(OutputComparator.matches(
                "{\"x\":1.001}", "{\"x\":1}", OutputComparator.Strategy.JSON)).isFalse();
    }

    @Test
    void jsonHandlesNullAndInvalidJsonFallback() {
        assertThat(OutputComparator.matches(null, "null", OutputComparator.Strategy.JSON)).isFalse();
        assertThat(OutputComparator.matches("{bad", "{bad", OutputComparator.Strategy.JSON)).isTrue();
        assertThat(OutputComparator.matches("{bad", "{worse", OutputComparator.Strategy.JSON)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"exact", "EXACT", " token ", "lines", "json"})
    void strategyOfAcceptsKnownNamesCaseInsensitively(String name) {
        OutputComparator.Strategy strategy = OutputComparator.strategyOf(name);
        assertThat(strategy).isNotNull();
    }

    @Test
    void strategyOfFallsBackToExactForUnknownName() {
        assertThat(OutputComparator.strategyOf(null)).isEqualTo(OutputComparator.Strategy.EXACT);
        assertThat(OutputComparator.strategyOf("")).isEqualTo(OutputComparator.Strategy.EXACT);
        assertThat(OutputComparator.strategyOf("unknown")).isEqualTo(OutputComparator.Strategy.EXACT);
    }
}
