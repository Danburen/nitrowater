package cn.nitrowater.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.Serializable;
import java.util.List;

/**
 * Owns the per-session account pool: capture on login, list, switch and remove.
 *
 * <p>Switching is a server-side operation. The pool already holds every account's identity and
 * tokens, so activation rebuilds the Spring Security context in place, rotates the session id
 * (session-fixation defence) and returns. No redirect to the Authorization Server is involved.</p>
 */
@Slf4j
@Service
public class AccountSessionService {

    /** Hard cap so a session cannot accumulate an unbounded token set. */
    public static final int MAX_ACCOUNTS = 5;

    private static final HttpSessionSecurityContextRepository CONTEXT_REPOSITORY =
            new HttpSessionSecurityContextRepository();

    private final MultiAccountAuthorizedClientRepository authorizedClients;
    private final UpstreamTokenService upstream;
    private final String registrationId;

    public AccountSessionService(MultiAccountAuthorizedClientRepository authorizedClients,
            UpstreamTokenService upstream,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        this.authorizedClients = authorizedClients;
        this.upstream = upstream;
        this.registrationId = registrationId;
    }

    /**
     * Captures the just-authenticated account into the pool and makes it active.
     *
     * <p>Called from the OIDC login success handler, after the OAuth2 client has already stored
     * the account's tokens under the same subject key.</p>
     *
     * @param request        current request
     * @param authentication authenticated OIDC login
     */
    public void registerLogin(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return;
        }
        AccountPool pool = pool(request);
        List<String> evicted = pool.upsert(AccountSlot.from(user), MAX_ACCOUNTS);
        for (String uid : evicted) {
            OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(registrationId, uid, request);
            if (client != null && client.getRefreshToken() != null) {
                upstream.revoke(client.getRefreshToken().getTokenValue());
            }
            authorizedClients.removeAuthorizedClient(registrationId, uid, request);
        }
        save(request, pool);
    }

    /**
     * Backfills a pool for sessions created before multi-account support was deployed.
     *
     * <p>Without this, an existing (still valid) Redis session would report "authenticated" but
     * expose no account entry, and the header would have nothing to render.</p>
     *
     * @param request        current request
     * @param authentication current authentication, possibly {@code null}
     */
    public void ensureSlot(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser)) {
            return;
        }
        AccountPool pool = pool(request);
        if (!pool.isEmpty()) {
            if (pool.sessionOwnerUid() == null) {
                // Sessions created before multi-account logout tracking have no owner; the active
                // account is the best available approximation.
                pool.markSessionOwner(pool.activeUid());
                save(request, pool);
            }
            return;
        }
        registerLogin(request, authentication);
    }

    /**
     * @param request current request
     * @return the pool, or an empty, unsaved pool when the session has none
     */
    public AccountPool pool(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(AccountPool.SESSION_ATTRIBUTE);
            if (value instanceof AccountPool existing) {
                return existing;
            }
        }
        return new AccountPool();
    }

    /**
     * @param request current request
     * @return view models for the SPA, in insertion order
     */
    public List<AccountView> views(HttpServletRequest request) {
        AccountPool pool = pool(request);
        String activeUid = pool.activeUid();
        return pool.accounts().stream()
                .map(account -> new AccountView(account.uid(), account.username(), account.name(),
                        account.roles(), account.picture(), account.uid().equals(activeUid)))
                .toList();
    }

    /**
     * @param request current request
     * @return the active account id, or {@code null}
     */
    public String activeUid(HttpServletRequest request) {
        return pool(request).activeUid();
    }

    /**
     * Activates an already signed-in account without contacting the Authorization Server.
     *
     * <p>The session id is rotated and the security context is rebuilt from the stored snapshot.
     * The tokens remain untouched, so the next proxied call works immediately.</p>
     *
     * @param request  current request
     * @param response current response
     * @param uid      account to activate
     * @throws ResponseStatusException 404 when the account is not in the pool
     */
    public void activate(HttpServletRequest request, HttpServletResponse response, String uid) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        AccountPool pool = requirePool(request);
        AccountSlot slot = pool.find(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));

        request.changeSessionId();

        Authentication authentication = slot.toAuthentication();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        CONTEXT_REPOSITORY.saveContext(context, request, response);
        SecurityContextHolder.setContext(context);

        pool.activate(uid);
        save(request, pool);
    }

    /**
     * Removes an account from the session and revokes its refresh token.
     *
     * <p>If the removed account was active and another account remains, the next one is activated
     * immediately. If it was the last account, token cleanup is deliberately deferred to the
     * subsequent {@code /logout}: RP-Initiated Logout refreshes the token set to obtain a current
     * {@code id_token} (the AS rejects a stale one), and that refresh needs a live refresh token.
     * The caller must therefore perform a full logout when {@code needLogout} is returned.</p>
     *
     * @param request  current request
     * @param response current response
     * @param uid      account to remove
     * @return remaining accounts and whether a full logout is required
     * @throws ResponseStatusException 404 when the account is not in the pool
     */
    public RemoveResult remove(HttpServletRequest request, HttpServletResponse response, String uid) {
        AccountPool pool = requirePool(request);
        pool.find(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));

        if (pool.accounts().size() == 1) {
            return new RemoveResult(views(request), pool.activeUid(), true);
        }

        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(registrationId, uid, request);
        boolean sessionOwner = uid.equals(pool.sessionOwnerUid());
        if (client != null && client.getRefreshToken() != null) {
            String refreshToken = client.getRefreshToken().getTokenValue();
            if (sessionOwner) {
                // The AS session still belongs to this account. Mint a current id_token before
                // revoking the refresh token, otherwise the later RP-Initiated Logout would send
                // a stale sid/id_token_hint and the AS would answer 400.
                String freshIdToken = upstream.refreshAndRevoke(refreshToken);
                if (freshIdToken != null) {
                    pool.setLogoutHint(freshIdToken);
                } else {
                    log.warn("Could not mint a fresh id_token for removed session owner {}", uid);
                }
            } else {
                upstream.revoke(refreshToken);
            }
        }
        Authentication target = UsernamePasswordAuthenticationToken.unauthenticated(uid, null);
        authorizedClients.removeAuthorizedClient(registrationId, target, request, response);

        boolean wasActive = uid.equals(pool.activeUid());
        pool.remove(uid);
        save(request, pool);

        if (wasActive) {
            activate(request, response, pool.activeUid());
        }
        return new RemoveResult(views(request), pool.activeUid(), false);
    }

    private static AccountPool requirePool(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(AccountPool.SESSION_ATTRIBUTE);
            if (value instanceof AccountPool existing) {
                return existing;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no account pool");
    }

    private static void save(HttpServletRequest request, AccountPool pool) {
        request.getSession(true).setAttribute(AccountPool.SESSION_ATTRIBUTE, pool);
    }

    /**
     * SPA-facing account summary.
     *
     * @param uid      account id
     * @param username login name
     * @param name     display name
     * @param roles    role codes
     * @param picture  stable avatar URL
     * @param active   whether this is the active account
     */
    public record AccountView(String uid, String username, String name, List<String> roles,
            String picture, boolean active) implements Serializable {
    }

    /**
     * Result of removing an account.
     *
     * @param accounts   remaining accounts
     * @param activeUid  active account id, {@code null} when none remain
     * @param needLogout whether the caller must perform a full logout
     */
    public record RemoveResult(List<AccountView> accounts, String activeUid, boolean needLogout)
            implements Serializable {
    }
}
