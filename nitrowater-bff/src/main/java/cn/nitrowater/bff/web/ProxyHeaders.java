package cn.nitrowater.bff.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Header policy shared by the two BFF proxies.
 *
 * <p>Requests are filtered by <strong>allowlist</strong>: a browser can set any header, so anything
 * a downstream does not actually need — {@code X-User-Uid}, {@code X-Forwarded-Proto/Host},
 * {@code X-Original-URL}, {@code Forwarded}, … — must never cross the hop.</p>
 *
 * <p>Responses drop hop-by-hop headers plus {@code set-cookie}: a downstream service must not be
 * able to write cookies on the BFF's own origin (that would let it overwrite the session cookie).</p>
 */
final class ProxyHeaders {

    /** Request headers forwarded downstream — deliberately minimal. */
    private static final Set<String> FORWARDED = Set.of(
            "accept",
            "accept-language",
            "content-type",
            "range",
            "if-none-match",
            "if-modified-since",
            "x-requested-with");

    /** Response headers that never reach the browser: hop-by-hop + credential-writing ones. */
    private static final Set<String> NOT_RELAYED = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length",
            "set-cookie", "authorization");

    private ProxyHeaders() {
    }

    /** @return whether a client-supplied request header may be forwarded downstream */
    static boolean isForwarded(String name) {
        return FORWARDED.contains(name.toLowerCase(Locale.ROOT));
    }

    /** @return whether a downstream response header may be relayed to the browser */
    static boolean isRelayableResponseHeader(String name) {
        return !NOT_RELAYED.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Feeds every allowed {@code (name, value)} pair of the incoming request to {@code sink}.
     *
     * @param request the inbound request
     * @param sink receives one call per allowed header value
     */
    static void forEachForwardedHeader(HttpServletRequest request, HeaderSink sink) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!isForwarded(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                sink.accept(name, values.nextElement());
            }
        }
    }

    /** Receives one forwarded header value. */
    @FunctionalInterface
    interface HeaderSink {
        void accept(String name, String value);
    }
}
