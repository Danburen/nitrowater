package cn.nitrowater.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;

/**
 * Permanent redirect from the retired {@code /docs/velochatx/**} mount.
 *
 * <p>VeloChatX documentation is served under {@code /plugin/velochatx/} by the docs container
 * (content baked into the {@code nitrowater-plugin-docs} image), which the BFF already fronts. The
 * old {@code /docs/velochatx/**} URL is kept as a redirect so bookmarks still open.</p>
 *
 * <p>There is no other {@code /docs/**} route: NitroWater documentation is a markdown-only corpus
 * for the assistant and has no published site.</p>
 */
@RestController
public class LegacyDocsRedirectController {

    /**
     * @param request the inbound legacy request
     * @return {@code 301} to the corresponding {@code /plugin/velochatx/...} URL
     */
    @GetMapping({"/docs/velochatx", "/docs/velochatx/**"})
    public ResponseEntity<Void> redirectLegacyVelochatx(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String suffix = path.substring("/docs/velochatx".length());
        String target = "/plugin/velochatx" + (suffix.isEmpty() ? "/" : suffix);
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            target += "?" + query;
        }
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(target))
                .build();
    }
}
