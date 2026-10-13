package cn.nitrowater.resource.application.dto;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;

import java.io.InputStream;

/**
 * A ready-to-render outcome for a single resource read.
 *
 * <p>Either a {@link #redirectUrl()} (serve via a presigned URL) or a {@link #stream()} (serve bytes
 * through this service); the remaining fields fully describe how the response must be shaped, so the
 * web layer only translates this into a transport response and never decides anything.</p>
 *
 * @param redirectUrl presigned GET URL to redirect to, or {@code null} to stream
 * @param stream byte stream to serve, or {@code null} when redirecting
 * @param contentType response content type (always {@code application/octet-stream} for media types
 *                    that must not render inline)
 * @param attachment whether the response must be forced to download ({@code Content-Disposition: attachment})
 * @param contentLength byte length, or {@code -1} when unknown
 * @param cacheControl cache directives derived from visibility and the signature window
 */
public record ResourceReadResult(
        String redirectUrl,
        InputStream stream,
        MediaType contentType,
        boolean attachment,
        long contentLength,
        CacheControl cacheControl) {

    /**
     * @param url presigned GET URL
     * @param cacheControl cache directives
     * @return a redirect outcome
     */
    public static ResourceReadResult redirect(String url, CacheControl cacheControl) {
        return new ResourceReadResult(url, null, null, false, -1, cacheControl);
    }

    /**
     * @param stream byte stream (caller closes)
     * @param contentType response content type
     * @param attachment whether to force a download
     * @param contentLength byte length, or {@code -1} when unknown
     * @param cacheControl cache directives
     * @return a streamed outcome
     */
    public static ResourceReadResult stream(InputStream stream, MediaType contentType, boolean attachment,
            long contentLength, CacheControl cacheControl) {
        return new ResourceReadResult(null, stream, contentType, attachment, contentLength, cacheControl);
    }
}
