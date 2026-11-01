package cn.nitrowater.server.ai.moderation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the answer redactor: known credential / contact / internal-address shapes are masked, and
 * ordinary technical prose is left untouched.
 */
class OutputRedactorTest {

    @Test
    void masksPrefixedKeys() {
        String text = "key=sk-abcdefghijklmnopqrstuvwx and AKIAABCDEFGHIJKLMNOP and ghp_abcdefghijklmnopqrstuvwxyz01";

        OutputRedactor.Result result = OutputRedactor.redact(text);

        assertThat(result.text())
                .contains("sk-***")
                .contains("AKIA***")
                .doesNotContain("abcdefghijklmnopqrstuvwx");
        assertThat(result.kinds()).contains("openai_key", "aws_key", "github_token");
    }

    @Test
    void masksJwtAndPrivateKeyBlock() {
        String text = "token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.c2lnbmF0dXJlLXZhbHVl\n"
                + "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\n-----END RSA PRIVATE KEY-----";

        OutputRedactor.Result result = OutputRedactor.redact(text);

        assertThat(result.text()).contains("eyJ***").doesNotContain("c2lnbmF0dXJlLXZhbHVl");
        assertThat(result.text()).doesNotContain("MIIEow");
        assertThat(result.kinds()).contains("jwt", "private_key");
    }

    @Test
    void masksCredentialAssignments() {
        OutputRedactor.Result result = OutputRedactor.redact("spring.datasource.password=Sup3rS3cret!");

        // The value is masked; trailing punctuation outside the value charset is intentionally kept.
        assertThat(result.text()).contains("password=***").doesNotContain("Sup3rS3cret");
        assertThat(result.kinds()).contains("credential");
    }

    @Test
    void masksContactAndInternalAddresses() {
        OutputRedactor.Result result = OutputRedactor.redact(
                "联系 admin@nitrowater.com 或 13800138000，内网 MySQL 在 192.168.1.10:3306。");

        assertThat(result.text())
                .contains("***@nitrowater.com")
                .doesNotContain("13800138000")
                .doesNotContain("192.168.1.10");
        assertThat(result.kinds()).contains("email", "phone", "private_ip");
    }

    @Test
    void leavesOrdinaryTechnicalProseAlone() {
        String text = "NitroWater 运行在 Java 25 上，端口 8080，文档版本 1.20.1；public API 在 localhost:5173。";

        OutputRedactor.Result result = OutputRedactor.redact(text);

        assertThat(result.text()).isEqualTo(text);
        assertThat(result.changed()).isFalse();
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(OutputRedactor.redact(null).text()).isNull();
        assertThat(OutputRedactor.redact("").changed()).isFalse();
    }
}
