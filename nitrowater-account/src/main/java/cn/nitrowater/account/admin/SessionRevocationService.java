package cn.nitrowater.account.admin;

import cn.nitrowater.account.admin.dto.RevokedSessionsVO;
import cn.nitrowater.account.service.security.SessionInvalidationListener;
import cn.nitrowater.core.infrastructure.domain.user.AccountAuditLog;
import cn.nitrowater.core.infrastructure.persistence.user.AccountAuditLogRepository;
import cn.nitrowater.core.infrastructure.session.SessionEpochStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Signs everybody out at once.
 *
 * <p>A browser session is only one of the three things that keep a user signed in, so a complete
 * revocation touches all of them:</p>
 *
 * <ol>
 *   <li>the <b>shared epoch</b> — BFF rejects every session stamped with the previous value on its
 *       next request, so the portal logs out immediately (and without a redeploy);</li>
 *   <li>the <b>OAuth2 authorizations</b> — removing the rows destroys the refresh tokens, so no
 *       client can silently mint new access tokens;</li>
 *   <li>the <b>SSO sessions</b> — deleting them also logs out the admin console and prevents a
 *       still-valid SSO session from walking {@code /oauth2/authorize} again.</li>
 * </ol>
 *
 * <p>This is deliberately global. Per-account revocation already exists as
 * {@link SessionInvalidationListener} (password / role changes);
 * this service is the emergency "everyone out" switch that used to be an environment variable.</p>
 */
@Slf4j
@Service
public class SessionRevocationService {

    /** Vendor table: Spring Authorization Server owns it and there is no JPA entity for it. */
    private static final String DELETE_ALL_AUTHORIZATIONS = "DELETE FROM oauth2_authorization";

    private final SessionEpochStore epochs;
    private final JdbcTemplate jdbcTemplate;
    private final AccountAuditLogRepository auditLogs;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final StringRedisTemplate redis;
    private final String namespace;

    public SessionRevocationService(SessionEpochStore epochs,
            JdbcTemplate jdbcTemplate,
            AccountAuditLogRepository auditLogs,
            FindByIndexNameSessionRepository<? extends Session> sessions,
            StringRedisTemplate redis,
            @Value("${spring.session.data.redis.namespace:nitrowater:account:session}")
            String namespace) {
        this.epochs = epochs;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogs = auditLogs;
        this.sessions = sessions;
        this.redis = redis;
        this.namespace = namespace;
    }

    /**
     * Revokes every session and returns what was removed.
     *
     * @param operatorUid  admin who triggered it (audit trail)
     * @param operatorName login name shown in the audit row
     * @param ipAddress    caller address for the audit row; {@code null} when unknown
     * @param deviceInfo   user agent for the audit row; {@code null} when unknown
     * @return counts plus the new epoch
     */
    public RevokedSessionsVO revokeAll(long operatorUid, String operatorName,
            String ipAddress, String deviceInfo) {
        // Rotate first: the cut-off is the click, and late logins re-stamp the new value. Both
        // services treat the epoch as authoritative, so a failure below cannot resurrect a session.
        String epoch = this.epochs.rotate();
        int authorizations = this.jdbcTemplate.update(DELETE_ALL_AUTHORIZATIONS);
        int ssoSessions = deleteAllSsoSessions();
        log.warn("Revoked all sessions by uid {}: epoch={}, {} OAuth2 authorization(s), {} SSO session(s)",
                operatorUid, epoch, authorizations, ssoSessions);
        writeAudit(operatorUid, operatorName, ipAddress, deviceInfo);
        return new RevokedSessionsVO(epoch, authorizations, ssoSessions);
    }

    /**
     * Records the action in {@code account_audit_log} through the shared JPA entity. An audit
     * failure must not block the revocation (which has already happened), so it is logged and
     * swallowed.
     */
    private void writeAudit(long operatorUid, String operatorName, String ipAddress, String deviceInfo) {
        try {
            AccountAuditLog entry = new AccountAuditLog();
            entry.setUserUid(operatorUid);
            entry.setAction("REVOKE_ALL_SESSIONS");
            entry.setActionTime(Instant.now());
            entry.setPerformedBy(truncate(operatorName, 50));
            entry.setIpAddress(truncate(ipAddress, 45));
            entry.setDeviceInfo(truncate(deviceInfo, 255));
            this.auditLogs.save(entry);
        } catch (RuntimeException e) {
            log.error("Failed to write the audit row for revoke-all by uid {}", operatorUid, e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Deletes every SSO session under the namespace.
     *
     * <p>{@code FindByIndexNameSessionRepository} can only look sessions up by principal, and there
     * is no "all sessions" API, so the ids are collected with {@code SCAN} (never {@code KEYS}) and
     * then removed through the repository, which also cleans the principal index entries.</p>
     */
    private int deleteAllSsoSessions() {
        String prefix = this.namespace + ":sessions:";
        String expiresPrefix = prefix + "expires:";
        Set<String> ids = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(1000).build();
        try (Cursor<String> cursor = this.redis.scan(options)) {
            cursor.forEachRemaining(key -> {
                if (!key.startsWith(expiresPrefix)) {
                    ids.add(key.substring(prefix.length()));
                }
            });
        }
        ids.forEach(id -> this.sessions.deleteById(id));
        return ids.size();
    }
}
