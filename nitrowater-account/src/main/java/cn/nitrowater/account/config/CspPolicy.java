package cn.nitrowater.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Content-Security-Policy for the hosted pages (login / register / admin shell).
 *
 * <p>The hosted login is a top-level {@code POST /login}. On success the server redirects to the
 * pending {@code /oauth2/authorize} request, which in turn redirects to the client's registered
 * {@code redirect_uri} — a different origin by design. Chrome evaluates {@code form-action}
 * across that redirect chain, so {@code form-action 'self'} silently aborts the navigation
 * <em>after</em> the server has already created the session: the user stays on the login page but
 * is logged in on reload. The callback origins therefore have to be listed explicitly.</p>
 *
 * <p>Entries are strict origins ({@code scheme://host[:port]}, http/https only, no wildcard, no
 * path) and are validated at startup, so a typo fails fast instead of weakening the policy or
 * breaking the login flow at runtime. Registering a new first-party application means adding the
 * origin of its {@code redirect_uri} to {@code app.csp.form-action-origins}.</p>
 */
@Component
public class CspPolicy {

    /** Every directive except {@code form-action}, which is the only configurable one. */
    private static final String STATIC_DIRECTIVES =
            "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; object-src 'none'; "
                    + "img-src 'self' data: blob:; font-src 'self' data:; "
                    + "style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
                    + "connect-src 'self'";

    private final String directives;

    /**
     * @param formActionOrigins extra origins allowed as a form-action target, already bound from
     *                          {@code app.csp.form-action-origins}
     * @throws IllegalArgumentException when an entry is not a plain http(s) origin
     */
    public CspPolicy(@Value("${app.csp.form-action-origins:}") List<String> formActionOrigins) {
        this.directives = build(formActionOrigins);
    }

    /** @return the ready-to-use {@code Content-Security-Policy} header value */
    public String directives() {
        return directives;
    }

    private static String build(List<String> origins) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : origins) {
            if (StringUtil.isNotBlank(raw)) {
                normalized.add(origin(raw));
            }
        }
        StringBuilder formAction = new StringBuilder("'self'");
        normalized.forEach(origin -> formAction.append(' ').append(origin));
        return "form-action " + formAction + "; " + STATIC_DIRECTIVES;
    }

    /**
     * Validates one configured entry and returns its canonical origin.
     *
     * @param raw configured entry, e.g. {@code https://nitrowater.cn}
     * @return the same origin without a trailing slash or a redundant default port
     */
    private static String origin(String raw) {
        String value = raw.trim();
        if (value.indexOf('*') >= 0 || value.indexOf(';') >= 0
                || value.indexOf(',') >= 0 || value.indexOf(' ') >= 0) {
            throw invalid(raw);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw invalid(raw);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean http = "http".equals(scheme) || "https".equals(scheme);
        boolean plainPath = uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath());
        int port = uri.getPort();
        if (!http || StringUtil.isBlank(uri.getHost())
                || uri.getUserInfo() != null || !plainPath
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || port < -1 || port > 65535) {
            throw invalid(raw);
        }
        boolean defaultPort = port == -1
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private static IllegalArgumentException invalid(String raw) {
        return new IllegalArgumentException("app.csp.form-action-origins entry must be a plain "
                + "http(s) origin (scheme://host[:port], no path, query, wildcard or comma): '" + raw + "'");
    }
}
