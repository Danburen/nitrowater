package cn.nitrowater.resource.application;

import cn.nitrowater.resource.config.ResourceProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the strict admin plugin-artifact allow-list: only {@code .jar}, a safe basename, a ZIP magic
 * number and the dedicated size cap. These rules are the only gate between the admin UI and storage,
 * so each rejection is asserted explicitly.
 */
class PluginArtifactPolicyTest {

    private static final long MAX = 64L * 1024 * 1024;

    private PluginArtifactPolicy newPolicy() {
        ResourceProperties properties = new ResourceProperties(
                "local", 600, 10L * 1024 * 1024, MAX, null, null, null, null, null);
        return new PluginArtifactPolicy(properties);
    }

    @Test
    void acceptsJarAndKeepsName() {
        assertThat(newPolicy().sanitizeName("VeloChatX-1.2.3.jar")).isEqualTo("VeloChatX-1.2.3.jar");
    }

    @Test
    void stripsAnyPathFromTheName() {
        assertThat(newPolicy().sanitizeName("C:\\build\\out\\VeloChatX.jar")).isEqualTo("VeloChatX.jar");
        assertThat(newPolicy().sanitizeName("../evil.jar")).isEqualTo("evil.jar");
    }

    @Test
    void rejectsNonJarExtensions() {
        PluginArtifactPolicy policy = newPolicy();
        assertThatThrownBy(() -> policy.sanitizeName("plugin.zip"))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("unsupported_type"));
        assertThatThrownBy(() -> policy.sanitizeName("plugin.exe"))
                .isInstanceOf(ResourceException.class);
        assertThatThrownBy(() -> policy.sanitizeName("plugin.jar.exe"))
                .isInstanceOf(ResourceException.class);
        assertThatThrownBy(() -> policy.sanitizeName("plugin"))
                .isInstanceOf(ResourceException.class);
    }

    @Test
    void rejectsUnsafeNames() {
        PluginArtifactPolicy policy = newPolicy();
        assertThatThrownBy(() -> policy.sanitizeName("foo..jar"))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("invalid_name"));
        assertThatThrownBy(() -> policy.sanitizeName("bad:name.jar"))
                .isInstanceOf(ResourceException.class);
        assertThatThrownBy(() -> policy.sanitizeName(" "))
                .isInstanceOf(ResourceException.class);
    }

    @Test
    void enforcesThePluginSizeCap() {
        PluginArtifactPolicy policy = newPolicy();
        assertThatCode(() -> policy.requireSize(MAX)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireSize(MAX + 1))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("too_large"));
        assertThatThrownBy(() -> policy.requireSize(0))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("length_required"));
        assertThatThrownBy(() -> policy.requireSize(-1))
                .isInstanceOf(ResourceException.class);
    }

    @Test
    void acceptsZipMagicAndRejectsOtherPayloads() {
        PluginArtifactPolicy policy = newPolicy();
        assertThatCode(() -> policy.requireJarMagic(new byte[] {'P', 'K', 3, 4})).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireJarMagic(new byte[] {'P', 'K', 5, 6})).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireJarMagic(new byte[] {'M', 'Z', 0, 0}))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("unsupported_type"));
        assertThatThrownBy(() -> policy.requireJarMagic(new byte[] {'P', 'K'}))
                .isInstanceOf(ResourceException.class);
    }

    @Test
    void validatesPluginKey() {
        PluginArtifactPolicy policy = newPolicy();
        assertThat(policy.sanitizeKey("velochatx")).isEqualTo("velochatx");
        assertThat(policy.sanitizeKey(" my-plugin-2 ")).isEqualTo("my-plugin-2");
        assertThatThrownBy(() -> policy.sanitizeKey("VeloChatX"))
                .isInstanceOf(ResourceException.class)
                .satisfies(ex -> assertThat(((ResourceException) ex).code()).isEqualTo("invalid_plugin_key"));
        assertThatThrownBy(() -> policy.sanitizeKey("-bad"))
                .isInstanceOf(ResourceException.class);
        assertThatThrownBy(() -> policy.sanitizeKey(""))
                .isInstanceOf(ResourceException.class);
    }

    @Test
    void fallsBackToDefaultCapWhenUnset() {
        ResourceProperties properties = new ResourceProperties(
                "local", 600, 10L * 1024 * 1024, 0, null, null, null, null, null);
        assertThat(properties.effectivePluginMaxSizeBytes())
                .isEqualTo(ResourceProperties.DEFAULT_PLUGIN_MAX_SIZE_BYTES);
    }
}
