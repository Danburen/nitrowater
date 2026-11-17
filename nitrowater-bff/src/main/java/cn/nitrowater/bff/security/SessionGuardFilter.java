package cn.nitrowater.bff.security;

import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import cn.nitrowater.lib.utils.codec.HashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps a session usable only while all three of its guarantees still hold.
 *
 * <ol>
 *   <li><b>Revocation epoch</b> — the session was created under the epoch currently stored in
 *       Redis ({@link SessionEpochStore}). An operator rotating it from the account admin console
 *       signs everybody out immediately, without a redeploy.</li>
 *   <li><b>Absolute age</b> — the login that created the session is younger than
 *       {@code app.session.absolute} (30 days). The idle timeout cannot express this: an active
 *       session slides forever, which is exactly what a stolen cookie would like.</li>
 *   <li><b>Device</b> — the browser still presents the fingerprint it logged in with. The SPA
 *       sends it on every data call ({@code X-Device-Fp}); a fingerprint that changed means another
 *       browser (or a cleared profile) is holding the cookie. Top-level navigations (downloads,
 *       {@code window.open}) cannot set that header, so the same id is also stored in a
 *       first-party cookie and accepted as a fallback.</li>
 * </ol>
 *
 * <p>Every rejection invalidates the session and leaves a reason behind, which
 * {@link JsonAuthenticationEntryPoint} turns into {@code X-Auth-Reason} + 401 so the SPA can tell
 * "session gone" from "forbidden".</p>
 */
public class SessionGuardFilter extends OncePerRequestFilter {

    /** Session attribute holding the release epoch the session was created under. */
    public static final String EPOCH_ATTRIBUTE = "cn.nitrowater.bff.sessionEpoch";

    /** Session attribute holding the instant the user last proved credentials. */
    public static final String AUTH_AT_ATTRIBUTE = "cn.nitrowater.bff.authenticatedAt";

    /** Session attribute holding the salted fingerprint hash captured at login. */
    public static final String DEVICE_ATTRIBUTE = "cn.nitrowater.bff.deviceHash";

    /** Session attribute holding the fingerprint handed over during the authorization redirect. */
    public static final String PENDING_DEVICE_ATTRIBUTE = "cn.nitrowater.bff.pendingDeviceFp";

    /** Request attribute the entry point turns into an {@code X-Auth-Reason}. */
    public static final String REASON_ATTRIBUTE = "cn.nitrowater.bff.authReason";

    /** Header the SPA sends with the fingerprint it signed in with. */
    public static final String DEVICE_HEADER = "X-Device-Fp";

    /**
     * Cookie holding the same fingerprint. {@code window.open}/{@code <a download>} are top-level
     * navigations and cannot carry a custom header, so the filter accepts this as a fallback.
     * Keep the name in sync with {@code nitrowater-web/src/auth/device.ts}.
     */
    public static final String DEVICE_COOKIE = "nitrowater_device_fp";

    /** Accepted fingerprint shape: the SPA's random id, no whitespace or punctuation beyond '-'. */
    private static final String FINGERPRINT_PATTERN = "[A-Za-z0-9-]{8,128}";

    /**
     * Private, non-configurable salt for the binding hash. It only keeps the browser id out of
     * Redis verbatim: making it a deployment knob would mean bumping it logs everybody out.
     */
    private static final String DEVICE_SALT = "cn.nitrowater.bff.device-binding";

    private final SessionEpochStore epochs;
    private final Duration absolute;

    /**
     * @param epochs   shared revocation epoch (Redis), read on every authenticated request
     * @param absolute {@code app.session.absolute}, hard cap measured from the login
     */
    public SessionGuardFilter(SessionEpochStore epochs, Duration absolute) {
        this.epochs = epochs;
        this.absolute = absolute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
            HttpSession session = request.getSession(false);
            String reason = session == null ? null : rejectionReason(session, request);
            if (reason != null) {
                invalidate(request);
                request.setAttribute(REASON_ATTRIBUTE, reason);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @param session current session
     * @param request current request
     * @return why the session may not be used any more, or {@code null} when it is still good
     */
    private String rejectionReason(HttpSession session, HttpServletRequest request) {
        if (!this.epochs.current().equals(session.getAttribute(EPOCH_ATTRIBUTE))) {
            return "session_epoch_mismatch";
        }
        if (!(session.getAttribute(AUTH_AT_ATTRIBUTE) instanceof Instant loginAt)
                || loginAt.plus(this.absolute).isBefore(Instant.now())) {
            // Missing timestamp = a session from before this rule existed: sign in again once.
            return "session_expired";
        }
        Object bound = session.getAttribute(DEVICE_ATTRIBUTE);
        if (bound == null || !guardsDevice(request)) {
            // No fingerprint was handed over at login, so there is nothing to compare against.
            return null;
        }
        return bound.equals(hashDevice(presentedFingerprint(request)))
                ? null
                : "device_changed";
    }

    /**
     * @param request current request
     * @return the fingerprint from the header, or from the fallback cookie for top-level navigations
     */
    private static String presentedFingerprint(HttpServletRequest request) {
        String header = request.getHeader(DEVICE_HEADER);
        if (isFingerprint(header)) {
            return header;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (DEVICE_COOKIE.equals(cookie.getName()) && isFingerprint(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * @param request current request
     * @return whether this path carries (or exposes) user data; logout and OIDC plumbing do not
     */
    private static boolean guardsDevice(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/bff/")
                ? !uri.startsWith("/bff/accounts/add")
                : uri.startsWith("/api/") && !uri.startsWith("/api/public/");
    }

    /**
     * Destroys the session and clears the in-memory security context.
     *
     * @param request current request
     */
    public static void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * @param value candidate fingerprint
     * @return whether it has the SPA's fingerprint shape
     */
    public static boolean isFingerprint(String value) {
        return value != null && value.matches(FINGERPRINT_PATTERN);
    }

    /**
     * @param fingerprint browser fingerprint, possibly {@code null}
     * @return the salted hash, or {@code null} when the fingerprint is missing or malformed
     */
    public static String hashDevice(String fingerprint) {
        return isFingerprint(fingerprint) ? HashUtil.hashWithSalt(fingerprint, DEVICE_SALT) : null;
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
