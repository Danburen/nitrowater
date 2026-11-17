package cn.nitrowater.bff.web;

import cn.nitrowater.core.web.legal.LegalDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Public legal documents (privacy policy / terms) served as plain text by the portal origin.
 *
 * <p>The files are <em>host state</em>, not build inputs: they live outside the jar (mounted at
 * {@code /etc/nitrowater/legal} in Docker, {@code deploy/content/legal} in dev) and are read from
 * disk on every request, so correcting a clause or filling in the operator's name is a plain file
 * edit — no rebuild, no restart, no cache to bust. They are deliberately not committed to git
 * (see {@code .gitignore}); a missing directory therefore means "not uploaded yet", which surfaces
 * as a 404 instead of shipping a half-filled policy from a previous image.</p>
 *
 * <p>The SSO account service serves the same texts on its own origin at the same path (see
 * {@code nitrowater-account}'s LegalDocumentController), and both read the same directory. The
 * documents cover the whole service (SSO included), so there is one version per deployment — never
 * a per-application copy.</p>
 *
 * <p>Responses bypass the JSON envelope on purpose — this is {@code text/plain} (see the exception
 * list in {@code AGENTS.md}).</p>
 */
@RestController
public class LegalDocumentController {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentController.class);

    private final Path directory;

    public LegalDocumentController(@Value("${app.legal.dir:}") String configured) {
        this.directory = LegalDocuments.resolveDirectory(configured);
        log.info("Legal documents directory: {}", directory);
    }

    /**
     * @param document {@code privacy} or {@code terms}
     * @return the file's current text, or 404 when the document is unknown or not uploaded
     */
    @GetMapping(value = "/api/public/legal/{document}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> get(@PathVariable String document) {
        if (!LegalDocuments.isSupported(document)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String text = LegalDocuments.read(directory, document);
            if (text == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    // Read-at-request-time: the next edit must be visible immediately, at every
                    // layer (browser, proxy, CDN). SecurityConfig also stamps /api/** with no-store.
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(text);
        } catch (IOException ex) {
            log.warn("Failed to read legal document {}: {}", document, ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .cacheControl(CacheControl.noStore())
                    .body("Legal document is temporarily unreadable.");
        }
    }
}
