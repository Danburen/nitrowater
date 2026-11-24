package cn.nitrowater.server.service.instance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the display-name format and its determinism / salt behaviour. */
class InstanceNameGeneratorTest {

    private final InstanceNameGenerator generator = new InstanceNameGenerator();

    @Test
    void nameIsStableAndHasTheDocumentedShape() {
        String first = generator.generate("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        String again = generator.generate("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

        assertThat(first).isEqualTo(again);
        assertThat(first).startsWith("instance-");
        assertThat(first.substring("instance-".length())).hasSize(6)
                .matches("[0-9a-zA-Z]{6}");
    }

    @Test
    void saltProducesADifferentName() {
        String base = generator.generate("uuid", 0);
        String salted = generator.generate("uuid", 1);

        assertThat(salted).isNotEqualTo(base);
        assertThat(salted).startsWith("instance-");
        assertThat(salted.substring("instance-".length())).hasSize(6);
    }
}
