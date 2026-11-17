package cn.nitrowater.account.security;

import cn.nitrowater.core.api.event.UserSessionsInvalidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Destroys every session of an account when its credentials or roles change.
 *
 * <p>An {@code OAuth2Authorization} row stores the login principal (with its roles) and re-issues
 * tokens from that snapshot, so the rows must go — that kills the refresh tokens. The browser's SSO
 * session and the legacy Redis tokens have to go too, or the same session could simply re-authorize.
 * The authorization service has no lookup by principal, hence the direct {@code principal_name}
 * query on the vendor-owned table (it holds the token {@code sub}, i.e. the uid).</p>
 *
 * <p>Sessions live in Spring Session (Redis) now, so they are found through the principal-name index
 * rather than an in-memory registry — which also means the registry of a freshly restarted node is
 * not blind to sessions created before it.</p>
 *
 * <p>Runs synchronously inside the publisher's transaction: a rollback keeps the sessions alive,
 * which is correct — the change itself did not commit.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionInvalidationListener {

    private static final String FIND_IDS_BY_PRINCIPAL =
            "SELECT id FROM oauth2_authorization WHERE principal_name = ?";

    private final OAuth2AuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    @EventListener
    public void onSessionsInvalidated(UserSessionsInvalidatedEvent event) {
        List<String> ids = jdbcTemplate.queryForList(
                FIND_IDS_BY_PRINCIPAL, String.class, String.valueOf(event.uid()));
        for (String id : ids) {
            OAuth2Authorization authorization = authorizationService.findById(id);
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        }
        int expired = expireSsoSessions(event.uid());
        log.info("Invalidated sessions of uid {} ({}): {} OIDC authorization(s) removed, {} SSO session(s) expired",
                event.uid(), event.reason(), ids.size(), expired);
    }

    /**
     * Deletes the account's SSO browser sessions ({@code nitrowater:account:session} in Redis).
     *
     * <p>Removing the authorizations alone is not enough: a still-valid SSO session could walk
     * {@code /oauth2/authorize} again and mint fresh tokens without re-entering the password. The
     * principal index is keyed by the authentication name, which {@code SsoUserPrincipal} returns
     * as the uid.</p>
     */
    private int expireSsoSessions(long uid) {
        Map<String, ? extends Session> byPrincipal = sessions.findByPrincipalName(String.valueOf(uid));
        byPrincipal.values().forEach(session -> sessions.deleteById(session.getId()));
        return byPrincipal.size();
    }
}
