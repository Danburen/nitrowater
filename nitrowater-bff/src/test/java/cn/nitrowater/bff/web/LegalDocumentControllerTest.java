package cn.nitrowater.bff.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDocumentControllerTest {

    @TempDir
    Path dir;

    @Test
    void servesWhitelistedDocumentAsUncachedPlainText() throws IOException {
        Files.writeString(dir.resolve("privacy.txt"), "隐私政策正文", StandardCharsets.UTF_8);
        LegalDocumentController controller = new LegalDocumentController(dir.toString());

        ResponseEntity<String> response = controller.get("privacy");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("隐私政策正文");
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void rejectsNamesOutsideTheWhitelist() {
        LegalDocumentController controller = new LegalDocumentController(dir.toString());

        assertThat(controller.get("..%2Fsecret").getStatusCode().value()).isEqualTo(404);
        assertThat(controller.get("../privacy").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void returns404WhenTheFileHasNotBeenUploadedYet() {
        LegalDocumentController controller = new LegalDocumentController(dir.toString());

        assertThat(controller.get("terms").getStatusCode().value()).isEqualTo(404);
    }
}
