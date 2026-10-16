package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.dto.ResourceReadResult;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Renders a {@link ResourceReadResult} into an HTTP response: a presigned redirect when the backend
 * can presign, otherwise a streamed body with the media type, disposition and cache directives the
 * application layer decided.
 *
 * <p>Shared by the read and admin controllers so the response policy is defined exactly once.</p>
 */
final class ResourceDownloadResponder {

    private ResourceDownloadResponder() {
    }

    /**
     * @param result the application read outcome
     * @return the transport response that renders it
     */
    static ResponseEntity<?> respond(ResourceReadResult result) {
        CacheControl cacheControl = result.cacheControl();
        if (result.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(result.redirectUrl()))
                    .cacheControl(cacheControl)
                    .build();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(result.contentType())
                .contentLength(result.contentLength())
                .cacheControl(cacheControl);
        if (result.attachment()) {
            // Force a download: a stored file (e.g. an SVG declaring HTML) must never render in the
            // browser, which would turn the shared resource origin into a stored-XSS sink. Carry the
            // original file name when we have one so the browser saves it under the right name.
            builder.header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(result.fileName()));
        }
        return builder.body(new InputStreamResource(result.stream()));
    }

    /** @return an {@code attachment} disposition, with a UTF-8 file name when one is known */
    private static String attachmentDisposition(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ContentDisposition.attachment().build().toString();
        }
        return ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString();
    }
}
