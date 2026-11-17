package cn.nitrowater.account.web;

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
 * Public legal documents (privacy policy / terms) served as plain text by the SSO origin.
 *
 * <p>This is the same path and the same files as the BFF's controller: both read the mounted
 * {@code /etc/nitrowater/legal} directory, so the portal and the hosted login / registration pages
 * can link to their own origin without a cross-domain hop — and without a second copy that could
 * drift. The documents describe the whole service (SSO included), which is exactly why the SSO
 * pages must show them rather than delegate to the portal.</p>
 *
 * <p>Only {@code privacy} / {@code terms} are addressable (see {@link LegalDocuments}); the
 * response is uncached so a corrected clause is visible on the next request. It is
 * {@code text/plain} and therefore intentionally outside the JSON envelope.</p>
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
                    // The texts are host state and may be edited at any time; never let a browser
                    // or the edge cache serve yesterday's policy.
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
