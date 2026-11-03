package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out the server-held access token for the current BFF session, and owns the decision that a
 * session is no longer usable.
 *
 * <p>The client registration rotates refresh tokens ({@code reuseRefreshTokens=false}), so the
 * refresh token is single-use: two requests of the same session refreshing at the same moment means
 * the loser presents an already-rotated token and the Authorization Server answers
 * {@code invalid_grant}. Outside a BFF that is a fatal, permanent state — this service makes it
 * recoverable in two ways:</p>
 *
 * <ul>
 *   <li><b>Serialized per session</b> — refreshes of one session never run concurrently, so at most
 *       one refresh reaches the Authorization Server at a time.</li>
 *   <li><b>Shared result</b> — a successful refresh is kept for {@link #SHARE_WINDOW} and adopted by
 *       the other requests of that session. A request whose session snapshot is a few seconds old
 *       (loaded before the winner wrote the rotated token back) therefore reuses the fresh token
 *       instead of rotating again. The same lookup doubles as the recovery path when a refresh is
 *       still rejected.</li>
 * </ul>
 *
 * <p>When no usable token can be produced — no authorized client, or a rejected refresh — the
 * session is invalidated here, exactly once, and {@link ReauthRequiredException} is thrown so the
 * caller answers 401. The alternative (Spring Security's default failure handler) deletes only the
 * authorized client and leaves a session that is authenticated but can never call upstream again,
 * which is what surfaced as a 500 on every later request.</p>
 *
 * <p>Single-instance assumption: the sharing window lives in this JVM. A second BFF instance would
 * fall back to its own refresh, which is safe (the loser only has to retry after re-reading the
 * session) but not optimized.</p>
 */
@Slf4j
@Component
public class BffTokenService {

    /** How long a successful refresh is shared with the other requests of the same session. */
    private static final Duration SHARE_WINDOW = Duration.ofMinutes(2);

    /** Fixed lock stripes: bounded memory, and two sessions only rarely collide. */
    private static final int LOCK_STRIPES = 64;

    /** Upper bound on shared entries; only the most recently refreshed sessions are kept. */
    private static final int MAX_SHARED_ENTRIES = 1024;

    private final Object[] locks = new Object[LOCK_STRIPES];

    private final Map<String, SharedClient> shared = new ConcurrentHashMap<>();

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final MultiAccountAuthorizedClientRepository authorizedClients;
    private final String registrationId;

    /**
     * @param authorizedClientManager   Spring Security manager that loads and refreshes the client
     * @param authorizedClients         session-backed token store, also used to adopt a shared token
     * @param registrationId            OIDC client registration to authorize against
     */
    public BffTokenService(OAuth2AuthorizedClientManager authorizedClientManager,
            MultiAccountAuthorizedClientRepository authorizedClients,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.authorizedClients = authorizedClients;
        this.registrationId = registrationId;
        for (int i = 0; i < this.locks.length; i++) {
            this.locks[i] = new Object();
        }
    }

    /**
     * Returns a usable access token for the given principal, refreshing it when needed.
     *
     * @param principal authenticated BFF session principal
     * @param request   current request (session store and lock key)
     * @param response  current response (passed through when a shared token is adopted)
     * @return the access token value
     * @throws ReauthRequiredException when the session has no usable token any more; the session has
     *                                 been invalidated by then, so the caller only renders a 401
     */
    public String accessToken(Authentication principal, HttpServletRequest request,
            HttpServletResponse response) {
        if (principal == null || principal instanceof AnonymousAuthenticationToken) {
            throw new ReauthRequiredException("not_authenticated");
        }
        String sessionId = sessionId(request);
        String principalName = principal.getName();
        synchronized (lockFor(sessionId)) {
            OAuth2AuthorizedClient adopted = sharedClient(sessionId, principalName);
            if (adopted != null) {
                // Another request of this session refreshed a moment ago: adopt its token set
                // instead of rotating the (already replaced) refresh token a second time.
                this.authorizedClients.saveAuthorizedClient(adopted, principal, request, response);
                return adopted.getAccessToken().getTokenValue();
            }
            try {
                OAuth2AuthorizedClient client = this.authorizedClientManager.authorize(
                        OAuth2AuthorizeRequest.withClientRegistrationId(this.registrationId)
                                .principal(principal)
                                .build());
                if (client == null || client.getAccessToken() == null) {
                    throw reauth(request, sessionId, principalName, "no_authorized_client");
                }
                share(sessionId, principalName, client);
                return client.getAccessToken().getTokenValue();
            } catch (OAuth2AuthorizationException ex) {
                // invalid_grant (rotated/revoked refresh token) or a refresh the AS refused. A
                // concurrent request may have refreshed while this one waited for the lock, in
                // which case its result is shared and this request does not have to fail.
                OAuth2AuthorizedClient recovered = sharedClient(sessionId, principalName);
                if (recovered != null) {
                    this.authorizedClients.saveAuthorizedClient(recovered, principal, request, response);
                    return recovered.getAccessToken().getTokenValue();
                }
                log.warn("Token refresh rejected for '{}': {}", principalName, ex.getError());
                throw reauth(request, sessionId, principalName, "refresh_rejected");
            }
        }
    }

    /**
     * Drops the session and turns the failure into a re-authentication signal.
     *
     * <p>Invalidating the whole session (not just the tokens) is what keeps the SPA honest: it
     * reloads into "signed out" instead of showing an identity that cannot call anything.</p>
     *
     * @param request       current request
     * @param sessionId     session lock/share key
     * @param principalName account, for the log line
     * @param reason        machine-readable cause
     * @return the exception to throw, pre-filled with the reason
     */
    private ReauthRequiredException reauth(HttpServletRequest request, String sessionId,
            String principalName, String reason) {
        this.shared.remove(sessionId);
        SessionEpochFilter.invalidate(request);
        log.info("Session of '{}' can no longer produce an access token ({})", principalName, reason);
        return new ReauthRequiredException(reason);
    }

    /**
     * @return the token set a concurrent request of this session just refreshed, or {@code null}
     *         when there is none or it is already expired
     */
    private OAuth2AuthorizedClient sharedClient(String sessionId, String principalName) {
        SharedClient entry = this.shared.get(sessionId);
        if (entry == null) {
            return null;
        }
        if (!entry.principalName().equals(principalName) || !entry.usableUntil().isAfter(Instant.now())) {
            this.shared.remove(sessionId, entry);
            return null;
        }
        OAuth2AuthorizedClient client = entry.client();
        Instant expiresAt = client.getAccessToken().getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            this.shared.remove(sessionId, entry);
            return null;
        }
        return client;
    }

    /**
     * Publishes a freshly obtained token set to the other requests of the same session.
     *
     * @param sessionId     session key
     * @param principalName account the token belongs to
     * @param client        authorized client returned by the manager
     */
    private void share(String sessionId, String principalName, OAuth2AuthorizedClient client) {
        if (this.shared.size() >= MAX_SHARED_ENTRIES) {
            Instant now = Instant.now();
            this.shared.values().removeIf(entry -> !entry.usableUntil().isAfter(now));
        }
        this.shared.put(sessionId,
                new SharedClient(principalName, client, Instant.now().plus(SHARE_WINDOW)));
    }

    /** @return the lock guarding refreshes of this session */
    private Object lockFor(String sessionId) {
        return this.locks[Math.floorMod(sessionId.hashCode(), LOCK_STRIPES)];
    }

    /**
     * @param request current request
     * @return the session id, or a fixed placeholder when the request has no session yet
     */
    private static String sessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? "no-session" : session.getId();
    }

    /**
     * A refresh result waiting to be adopted by the other requests of the same session.
     *
     * @param principalName account the token set belongs to
     * @param client        authorized client (access + rotated refresh token)
     * @param usableUntil   instant after which this entry must not be adopted
     */
    private record SharedClient(String principalName, OAuth2AuthorizedClient client, Instant usableUntil) {
    }
}
