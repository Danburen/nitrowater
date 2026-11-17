package cn.nitrowater.bff.web;

import cn.nitrowater.core.web.legal.LegalDocumentSummary;
import cn.nitrowater.core.web.legal.LegalDocumentService;
import cn.nitrowater.lib.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDocumentControllerTest {

    @TempDir
    Path dir;

    @Test
    void servesWhitelistedDocumentAsUncachedPlainText() throws IOException {
        write("privacy.txt", "隐私政策\n\n隐私政策正文");
        LegalDocumentController controller = controller(dir);

        ResponseEntity<String> response = controller.get("privacy");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("隐私政策正文");
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void listsEveryPublishedDocument() throws IOException {
        write("privacy.txt", "隐私政策\n\n隐私政策正文");
        write("terms.txt", "用户协议\n\n用户协议正文");
        LegalDocumentController controller = controller(dir);

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ApiResponse<List<LegalDocumentSummary>> body = controller.index(servletResponse);

        assertThat(servletResponse.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(servletResponse.getStatus()).isEqualTo(200);
        assertThat(body.getSuccess()).isTrue();
        assertThat(body.getData()).extracting(LegalDocumentSummary::key)
                .containsExactlyInAnyOrder("privacy", "terms");
        assertThat(body.getData()).extracting(LegalDocumentSummary::title)
                .containsExactlyInAnyOrder("隐私政策", "用户协议");
    }

    @Test
    void listsNothingBeforeAnythingHasBeenUploaded() {
        LegalDocumentController controller = controller(dir.resolve("missing"));

        assertThat(controller.index(new MockHttpServletResponse()).getData()).isEmpty();
        assertThat(controller.get("terms").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsKeysOutsideThePattern() {
        LegalDocumentController controller = controller(dir);

        assertThat(controller.get("..%2Fsecret").getStatusCode().value()).isEqualTo(404);
        assertThat(controller.get("../privacy").getStatusCode().value()).isEqualTo(404);
        assertThat(controller.get("Privacy").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void returns404WhenTheFileHasNotBeenUploadedYet() {
        LegalDocumentController controller = controller(dir);

        assertThat(controller.get("terms").getStatusCode().value()).isEqualTo(404);
    }

    private LegalDocumentController controller(Path directory) {
        return new LegalDocumentController(new LegalDocumentService(directory.toString()));
    }

    private void write(String name, String text) throws IOException {
        Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
    }
}
