package cn.nitrowater.server.service.instance;

import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.enums.InstanceCredentialStatus;
import cn.nitrowater.server.infra.enums.InstanceScope;
import cn.nitrowater.server.infra.exception.InstanceException;
import cn.nitrowater.server.infra.repository.InstanceCredentialRepository;
import cn.nitrowater.server.infra.specification.InstanceCredentialSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle of user-level VeloChatX api-keys: issue, verify and revoke.
 *
 * <p>The plaintext key is generated here, shown to the admin exactly once and never persisted —
 * only its SHA-256 hash is stored, and verification is an indexed exact lookup by that hash. A key
 * fixes the owner but no instance id; instances bind themselves on first heartbeat (see
 * {@link InstanceRegistryService}).</p>
 */
@Service
public class InstanceCredentialService {

    private static final String KEY_PREFIX_TEXT = "nw_live_";
    private static final int KEY_RANDOM_BYTES = 32;
    private static final int MAX_PAGE_SIZE = 100;

    private final InstanceCredentialRepository credentials;
    private final SecureRandom random = new SecureRandom();

    public InstanceCredentialService(InstanceCredentialRepository credentials) {
        this.credentials = credentials;
    }

    /**
     * Issues a new user-level api-key. The returned plaintext is the only time the full key exists.
     *
     * @param remark    optional admin note
     * @param ownerUid  logical owner (account uid), may be {@code null}
     * @param scopes    granted scopes; defaults to all when empty
     * @return the saved credential together with the one-time plaintext key
     */
    @Transactional
    public IssuedCredential issue(String remark, Long ownerUid, Set<InstanceScope> scopes) {
        String raw = generateRawKey();
        Set<InstanceScope> granted = (scopes == null || scopes.isEmpty())
                ? Set.of(InstanceScope.values())
                : scopes;

        InstanceCredential credential = new InstanceCredential();
        credential.setKeyId(generateKid());
        credential.setKeyHash(sha256Hex(raw));
        credential.setKeyPrefix(prefixOf(raw));
        credential.setKeyLast4(last4Of(raw));
        credential.setScopes(InstanceScope.serialize(granted));
        credential.setOwnerUid(ownerUid);
        credential.setRemark(remark);
        credential.setCreatedAt(Instant.now());
        InstanceCredential saved = credentials.save(credential);
        return new IssuedCredential(saved, raw);
    }

    /**
     * Verifies a presented raw key. Revoked keys are rejected; rotated keys stay valid during the
     * grace window (command execution is gated separately by scope).
     *
     * @param rawKey full plaintext key (without surrounding {@code Bearer })
     * @return the credential when the key is known and not revoked
     */
    @Transactional(readOnly = true)
    public Optional<InstanceCredential> verify(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        byte[] computed = sha256(rawKey);
        String hash = HexFormat.of().formatHex(computed);
        return credentials.findByKeyHash(hash)
                .filter(credential -> credential.getStatus() != InstanceCredentialStatus.REVOKED)
                .filter(credential -> MessageDigest.isEqual(
                        computed, HexFormat.of().parseHex(credential.getKeyHash())));
    }

    /**
     * Revokes a credential. Revocation is immediate for command execution and takes effect on the
     * next heartbeat for reporting (the api-key cache is invalidated with the status change).
     *
     * @param id credential primary key
     * @return the updated credential
     */
    @Transactional
    public InstanceCredential revoke(Long id) {
        InstanceCredential credential = require(id);
        credential.setStatus(InstanceCredentialStatus.REVOKED);
        credential.setRevokedAt(Instant.now());
        return credentials.save(credential);
    }

    /**
     * @param keyword optional kid / prefix / remark substring
     * @param status  optional lifecycle filter
     * @param page    zero-based page index
     * @param size    page size
     * @return one page of credentials (never exposing the key hash or plaintext)
     */
    @Transactional(readOnly = true)
    public Page<InstanceCredential> list(String keyword, InstanceCredentialStatus status, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)), sort);
        return credentials.findAll(InstanceCredentialSpec.of(status, keyword), pageable);
    }

    /**
     * @param id credential primary key
     * @return the credential
     * @throws InstanceException when not found
     */
    @Transactional(readOnly = true)
    public InstanceCredential require(Long id) {
        return credentials.findById(id)
                .orElseThrow(() -> InstanceException.notFound("Instance credential not found: " + id));
    }

    /** @return {@code nw_live_} + 32 random bytes encoded as unpadded base64url (43 chars) */
    private String generateRawKey() {
        byte[] bytes = new byte[KEY_RANDOM_BYTES];
        random.nextBytes(bytes);
        return KEY_PREFIX_TEXT + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** @return a unique display id (kid) for the credential */
    private String generateKid() {
        String kid;
        do {
            kid = "kid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } while (credentials.existsByKeyId(kid));
        return kid;
    }

    private static String prefixOf(String raw) {
        String body = raw.substring(KEY_PREFIX_TEXT.length());
        return KEY_PREFIX_TEXT + body.substring(0, 4);
    }

    private static String last4Of(String raw) {
        return raw.substring(raw.length() - 4);
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * The one-time result of a credential issuance.
     *
     * @param credential saved row (hash only)
     * @param rawKey     plaintext key, returned to the admin once and never stored
     */
    public record IssuedCredential(InstanceCredential credential, String rawKey) {
    }
}
