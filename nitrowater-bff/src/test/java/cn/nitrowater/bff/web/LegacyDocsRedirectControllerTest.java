package cn.nitrowater.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyDocsRedirectControllerTest {

    private final LegacyDocsRedirectController controller = new LegacyDocsRedirectController();

    @Test
    void redirectsRetiredDocsRootToPluginSite() {
        ResponseEntity<Void> response = controller.redirectLegacyVelochatx(request("/docs/velochatx"));

        assertThat(response.getStatusCode().value()).isEqualTo(301);
        assertThat(response.getHeaders().getLocation()).hasToString("/plugin/velochatx/docs/");
    }

    @Test
    void redirectsPageAndKeepsQuery() {
        MockHttpServletRequest request = request("/docs/velochatx/guide/config/main.html");
        request.setQueryString("lang=zh");

        ResponseEntity<Void> response = controller.redirectLegacyVelochatx(request);

        assertThat(response.getStatusCode().value()).isEqualTo(301);
        assertThat(response.getHeaders().getLocation())
                .hasToString("/plugin/velochatx/docs/guide/config/main.html?lang=zh");
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
        return request;
    }
}
