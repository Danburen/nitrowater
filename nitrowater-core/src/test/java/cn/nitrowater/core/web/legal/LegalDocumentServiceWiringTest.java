package cn.nitrowater.core.web.legal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bean wiring: {@code @Service} + {@code @Value("${app.legal.dir:}")} must work when the
 * consumer imports the class explicitly, and the blank default must not fail startup.
 *
 * <p>{@code @Import} is exactly how the BFF pulls the bean in (a broad component scan is not an
 * option: {@code cn.nitrowater.core.web} is excluded where services scan {@code cn.nitrowater}).</p>
 */
class LegalDocumentServiceWiringTest {

    @TempDir
    Path dir;

    @Configuration
    @Import(LegalDocumentService.class)
    static class ImportedConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ImportedConfiguration.class);

    @Test
    void bindsTheConfiguredDirectory() {
        runner.withPropertyValues("app.legal.dir=" + dir)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LegalDocumentService.class);
                });
    }

    @Test
    void startsWithoutTheProperty() {
        // app.legal.dir is blank by default (bootRun auto-detects); a missing directory is an empty
        // index at request time, not a startup failure.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LegalDocumentService.class);
        });
    }
}
