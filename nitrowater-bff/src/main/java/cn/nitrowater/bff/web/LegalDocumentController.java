package cn.nitrowater.bff.web;

import cn.nitrowater.core.web.legal.LegalDocumentSummary;
import cn.nitrowater.core.web.legal.LegalDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Public legal documents on the portal origin: the index plus one plain-text body per document.
 *
 * <p>The files are host state (mounted at {@code /etc/nitrowater/legal} in Docker,
 * {@code deploy/content/legal} in dev), not committed and not baked into the image, and read per
 * request — so an edit needs no rebuild, restart or cache bust, and a missing directory surfaces as an
 * empty index instead of a stale policy from an old image.</p>
 *
 * <p>Bodies are {@code text/plain} and therefore outside the JSON envelope (see the exception list in
 * {@code AGENTS.md}); the index is normal JSON and is enveloped.</p>
 */
@RestController
public class LegalDocumentController {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentController.class);

    private final LegalDocuments documents;

    public LegalDocumentController(LegalDocuments documents) {
        this.documents = documents;
    }

    /**
     * @return every published document; empty before anything is uploaded
     */
    @GetMapping("/api/public/legal")
    public ResponseEntity<List<LegalDocumentSummary>> index() {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(documents.index());
        } catch (IOException ex) {
            log.warn("Failed to list legal documents: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * @param key document key from the index
     * @return the file's current text, or 404 when the key is malformed or nothing publishes it
     */
    @GetMapping(value = "/api/public/legal/{key}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> get(@PathVariable String key) {
        try {
            String text = documents.read(key);
            if (text == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    // Read per request: the next edit must be visible at every layer.
                    .cacheControl(CacheControl.noStore())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(text);
        } catch (IOException ex) {
            log.warn("Failed to read legal document {}: {}", key, ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .cacheControl(CacheControl.noStore())
                    .body("Legal document is temporarily unreadable.");
        }
    }
}
