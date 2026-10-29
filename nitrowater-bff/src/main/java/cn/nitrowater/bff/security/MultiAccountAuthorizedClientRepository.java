package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-backed authorized-client store that keeps <strong>one token set per account</strong>.
 *
 * <p>The stock {@code HttpSessionOAuth2AuthorizedClientRepository} keys the map only by
 * {@code registrationId}, so a browser session can hold exactly one account. That is the
 * structural reason account switching is impossible today: logging in as B overwrites A's
 * access/refresh tokens while the security context still carries A's identity.</p>
 *
 * <p>This repository keys by {@code registrationId + "|" + principalName}. The OIDC subject
 * ({@code sub}, surfaced as {@code OidcUser#getName()}) is the account id, so two logins for
 * different accounts coexist in the same HTTP session. The session is still the only store; the
 * browser still holds no token.</p>
 */
public class MultiAccountAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    /** Session attribute holding {@code Map<String, OAuth2AuthorizedClient>}. */
    public static final String SESSION_ATTRIBUTE =
            MultiAccountAuthorizedClientRepository.class.getName() + ".AUTHORIZED_CLIENTS";

    private static final char KEY_SEPARATOR = '|';

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId,
            Authentication principal, HttpServletRequest request) {
        if (registrationId == null || principal == null) {
            return null;
        }
        Map<String, OAuth2AuthorizedClient> clients = read(request);
        if (clients == null) {
            return null;
        }
        return (T) clients.get(key(registrationId, principal.getName()));
    }

    /**
     * Reads a token set for an explicit account id.
     *
     * <p>Used when the caller is not the account being acted on (for example revoking an inactive
     * account's refresh token during account removal).</p>
     */
    @SuppressWarnings("unchecked")
    public OAuth2AuthorizedClient loadAuthorizedClient(String registrationId, String uid,
            HttpServletRequest request) {
        if (registrationId == null || uid == null) {
            return null;
        }
        Map<String, OAuth2AuthorizedClient> clients = read(request);
        return clients == null ? null : clients.get(key(registrationId, uid));
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient,
            Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        if (authorizedClient == null || principal == null) {
            return;
        }
        Map<String, OAuth2AuthorizedClient> clients = writable(request);
        clients.put(key(authorizedClient.getClientRegistration().getRegistrationId(),
                principal.getName()), authorizedClient);
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        if (registrationId == null || principal == null) {
            return;
        }
        Map<String, OAuth2AuthorizedClient> clients = writable(request);
        clients.remove(key(registrationId, principal.getName()));
    }

    /**
     * Removes the token set for an explicit account id.
     *
     * <p>Used when the pool evicts an account and the caller has no {@link Authentication} for it.</p>
     *
     * @param registrationId client registration id
     * @param uid            account id
     * @param request        current request
     */
    public void removeAuthorizedClient(String registrationId, String uid, HttpServletRequest request) {
        if (registrationId == null || uid == null) {
            return;
        }
        Map<String, OAuth2AuthorizedClient> clients = writable(request);
        clients.remove(key(registrationId, uid));
    }

    /**
     * @param request current request
     * @return every token set in the session, in insertion order; empty when there is none
     */
    public Collection<OAuth2AuthorizedClient> loadAll(HttpServletRequest request) {
        Map<String, OAuth2AuthorizedClient> clients = read(request);
        return clients == null ? new ArrayList<>() : new ArrayList<>(clients.values());
    }

    /** Drops every token set. Used during logout, while the session is still readable. */
    public void removeAll(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    /**
     * @return the stored map, or {@code null} when the session does not exist yet
     */
    private static Map<String, OAuth2AuthorizedClient> read(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return read(session);
    }

    /**
     * Returns a mutable map and writes it back into the (possibly new) session.
     *
     * <p>A fresh map is always created: the object read from Redis-backed sessions must not be
     * assumed mutable, and re-setting the attribute guarantees Spring Session sees the change.</p>
     */
    private static Map<String, OAuth2AuthorizedClient> writable(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Map<String, OAuth2AuthorizedClient> clients = new LinkedHashMap<>();
        Map<String, OAuth2AuthorizedClient> existing = read(session);
        if (existing != null) {
            clients.putAll(existing);
        }
        session.setAttribute(SESSION_ATTRIBUTE, clients);
        return clients;
    }

    private static Map<String, OAuth2AuthorizedClient> read(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, OAuth2AuthorizedClient> clients = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof OAuth2AuthorizedClient client) {
                clients.put(key, client);
            }
        }
        return clients;
    }

    private static String key(String registrationId, String uid) {
        return registrationId + KEY_SEPARATOR + uid;
    }
}
