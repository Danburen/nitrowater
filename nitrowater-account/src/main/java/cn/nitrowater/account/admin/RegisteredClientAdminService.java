package cn.nitrowater.account.admin;

import cn.nitrowater.account.admin.dto.ClientCreateReq;
import cn.nitrowater.account.admin.dto.ClientRegistrationVO;
import cn.nitrowater.account.admin.dto.ClientSecretVO;
import cn.nitrowater.account.admin.dto.ClientUpdateReq;
import cn.nitrowater.account.admin.dto.ClientVO;
import cn.nitrowater.account.admin.dto.LegalLinkReq;
import cn.nitrowater.account.admin.dto.LegalLinkVO;
import cn.nitrowater.account.config.AuthorizationServerConfig;
import cn.nitrowater.account.infra.ClientLegalLink;
import cn.nitrowater.account.service.ClientLegalLinkInput;
import cn.nitrowater.account.service.ClientLegalLinkService;
import cn.nitrowater.core.infrastructure.domain.oauth2.RegisteredClientRow;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.infrastructure.persistence.oauth2.RegisteredClientRowRepository;
import cn.nitrowater.core.infrastructure.persistence.specification.RegisteredClientSpec;
import cn.nitrowater.lib.api.BaseResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Admin console service for registering and managing OIDC applications (clients).
 *
 * <p>Lives in {@code nitrowater-account} because the OAuth2 client tables are owned by the SSO
 * database. It is a thin, transactional facade over {@link RegisteredClientRepository} plus a
 * {@link JdbcTemplate} for the two operations the repository does not expose (list-all and
 * delete). All authorization (only {@code ROLE_SUPER_ADMIN} may reach it) is enforced by the
 * Servlet security chain, not here.</p>
 *
 * <p>Secret policy: a confidential client's secret is generated server-side, stored only as a
 * hash (delegating encoder), and the plaintext is returned exactly once (create / reset-secret).</p>
 *
 * <p>Guards:</p>
 * <ul>
 *   <li>{@code client_id} must be unique;</li>
 *   <li>the first-party {@code nitrowater-bff} client can never be deleted;</li>
 *   <li>deleting an application also clears its authorizations and consent records, so no
 *       dangling tokens reference a removed client.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RegisteredClientAdminService {

    /** The public-client authentication method (a client that holds no secret). */
    private static final String PUBLIC_AUTH_METHOD = "none";

    /** Entropy (bytes) of a generated client secret. */
    private static final int SECRET_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Same encoder family the seeder uses, so {@code {bcrypt}} hashes verify identically. */
    private static final PasswordEncoder CLIENT_SECRET_ENCODER =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(30);

    /** Upper bound on a client listing page size. */
    private static final int MAX_PAGE_SIZE = 100;

    private final RegisteredClientRepository registeredClientRepository;
    private final RegisteredClientRowRepository registeredClientRows;
    private final ClientLegalLinkService clientLegalLinks;
    private final JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ queries

    /**
     * Paged registered applications, ordered by {@code client_id}.
     *
     * <p>The page and the keyword filter run as a JPA query over
     * {@link RegisteredClientRow}; each row is then hydrated through the Spring Authorization Server
     * repository so typed settings (proof key, consent, TTLs) keep coming from one parser.</p>
     *
     * @param keyword optional case-insensitive client_id / client_name substring
     * @param page zero-based page index
     * @param size page size, clamped to the maximum
     * @return one page of clients
     */
    @Transactional(readOnly = true)
    public Page<ClientVO> listClients(String keyword, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Specification<RegisteredClientRow> filters = RegisteredClientSpec.of(keyword);

        Page<RegisteredClientRow> matched = registeredClientRows.findAll(filters,
                PageRequest.of(safePage, safeSize, Sort.by("clientId")));
        List<String> clientIds = matched.getContent().stream()
                .map(RegisteredClientRow::getClientId)
                .toList();
        // One extra query for the whole page instead of one per client.
        var linksByClient = clientLegalLinks.findByClientIds(clientIds);
        List<ClientVO> rows = matched.getContent().stream()
                .map(row -> registeredClientRepository.findById(row.getId()))
                .filter(Objects::nonNull)
                .map(client -> toVO(client, linksByClient.getOrDefault(client.getClientId(), List.of())))
                .toList();
        return new PageImpl<>(rows, matched.getPageable(), matched.getTotalElements());
    }

    /** One application by its internal id. */
    @Transactional(readOnly = true)
    public ClientVO getClient(String id) {
        RegisteredClient client = requireClient(id);
        return toVO(client, clientLegalLinks.findByClientId(client.getClientId()));
    }

    // ------------------------------------------------------------------ commands

    /**
     * Registers a new application.
     *
     * <p>A confidential client gets a freshly generated secret whose plaintext is returned once;
     * a public client (only the {@code none} authentication method) has no secret.</p>
     */
    @Transactional
    public ClientRegistrationVO createClient(ClientCreateReq req) {
        String clientId = req.clientId().trim();
        if (registeredClientRepository.findByClientId(clientId) != null) {
            throw new BizException(BaseResponseCode.CLIENT_ALREADY_EXISTS_WITH_ARGS, clientId);
        }

        Set<String> authMethods = normalized(req.clientAuthenticationMethods());
        boolean confidential = isConfidential(authMethods);
        String plainSecret = confidential ? generateSecret() : null;

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(req.clientName().trim());
        if (confidential) {
            builder.clientSecret(CLIENT_SECRET_ENCODER.encode(plainSecret));
        }
        applySettings(builder, authMethods,
                req.authorizationGrantTypes(), req.redirectUris(), req.postLogoutRedirectUris(),
                req.scopes(), confidential);

        RegisteredClient client = builder.build();
        registeredClientRepository.save(client);
        clientLegalLinks.replace(client.getClientId(), inputsOf(req.legalLinks()));
        return new ClientRegistrationVO(
                toVO(client, clientLegalLinks.findByClientId(client.getClientId())), plainSecret);
    }

    /**
     * Updates an application in place. Collections are replaced, the {@code client_id} is immutable,
     * and the existing secret is preserved. If the client is switched from public to confidential a
     * new secret is minted and returned once.
     */
    @Transactional
    public ClientRegistrationVO updateClient(String id, ClientUpdateReq req) {
        RegisteredClient existing = requireClient(id);
        if (AuthorizationServerConfig.BFF_CLIENT_ID.equals(existing.getClientId())) {
            // Rebuilt from configuration on every start; the legal links are the only part the
            // seeder does not own, so everything else is left exactly as the seeder wrote it.
            clientLegalLinks.replace(existing.getClientId(), inputsOf(req.legalLinks()));
            return new ClientRegistrationVO(
                    toVO(existing, clientLegalLinks.findByClientId(existing.getClientId())), null);
        }
        Set<String> authMethods = normalized(req.clientAuthenticationMethods());
        boolean confidential = isConfidential(authMethods);
        boolean mintSecret = confidential && existing.getClientSecret() == null;
        String plainSecret = mintSecret ? generateSecret() : null;

        RegisteredClient.Builder builder = RegisteredClient.withId(existing.getId())
                .clientId(existing.getClientId())
                .clientIdIssuedAt(existing.getClientIdIssuedAt())
                .clientName(req.clientName().trim());
        if (confidential) {
            String secret = mintSecret ? CLIENT_SECRET_ENCODER.encode(plainSecret) : existing.getClientSecret();
            builder.clientSecret(secret);
            if (!mintSecret && existing.getClientSecretExpiresAt() != null) {
                builder.clientSecretExpiresAt(existing.getClientSecretExpiresAt());
            }
        }
        applySettings(builder, authMethods,
                req.authorizationGrantTypes(), req.redirectUris(), req.postLogoutRedirectUris(),
                req.scopes(), confidential);

        RegisteredClient updated = builder.build();
        registeredClientRepository.save(updated);
        clientLegalLinks.replace(updated.getClientId(), inputsOf(req.legalLinks()));
        return new ClientRegistrationVO(
                toVO(updated, clientLegalLinks.findByClientId(updated.getClientId())), plainSecret);
    }

    /** Rotates a confidential client's secret; the new plaintext is returned once. */
    @Transactional
    public ClientSecretVO resetSecret(String id) {
        RegisteredClient existing = requireClient(id);
        if (AuthorizationServerConfig.BFF_CLIENT_ID.equals(existing.getClientId())) {
            // The secret comes from BFF_CLIENT_SECRET; rotating it here would only break the BFF
            // until the next start puts the configured one back.
            throw new BizException(BaseResponseCode.CLIENT_MANAGED_BY_CONFIG, existing.getClientId());
        }
        if (!isConfidential(authMethodsOf(existing))) {
            throw new BizException(BaseResponseCode.CLIENT_NOT_CONFIDENTIAL, existing.getClientId());
        }
        String plainSecret = generateSecret();
        RegisteredClient updated = RegisteredClient.from(existing)
                .clientSecret(CLIENT_SECRET_ENCODER.encode(plainSecret))
                .build();
        registeredClientRepository.save(updated);
        return new ClientSecretVO(plainSecret);
    }

    /**
     * Deletes an application and its authorizations / consents.
     *
     * <p>The first-party {@code nitrowater-bff} client is protected and cannot be deleted.</p>
     */
    @Transactional
    public void deleteClient(String id) {
        RegisteredClient existing = requireClient(id);
        if (AuthorizationServerConfig.BFF_CLIENT_ID.equals(existing.getClientId())) {
            throw new BizException(BaseResponseCode.CAN_NOT_DELETE_BUILTIN_CLIENT, existing.getClientId());
        }
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", id);
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", id);
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
        // The legal rows are keyed by client_id, not by the vendor PK.
        clientLegalLinks.deleteByClientId(existing.getClientId());
    }

    // ------------------------------------------------------------------ helpers

    private RegisteredClient requireClient(String id) {
        RegisteredClient client = registeredClientRepository.findById(id);
        if (client == null) {
            throw new BizException(BaseResponseCode.CLIENT_NOT_FOUND_WITH_ARGS, id);
        }
        return client;
    }

    /**
     * Applies the shared collection fields plus the derived auth settings onto a builder.
     *
     * <p>Nothing here is operator-configurable: PKCE is mandatory for public clients (their only
     * protection) and off for confidential ones, no consent page is shown, and the token lifetimes
     * are the constants below. The seeded first-party client would overwrite anything else on the
     * next start, so exposing these as inputs only produced settings that silently reverted.</p>
     */
    private static void applySettings(RegisteredClient.Builder builder, Set<String> authMethods,
            List<String> grantTypes, List<String> redirectUris, List<String> postLogoutRedirectUris,
            List<String> scopes, boolean confidential) {
        for (String method : authMethods) {
            builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method));
        }
        for (String grantType : normalized(grantTypes)) {
            builder.authorizationGrantType(new AuthorizationGrantType(grantType));
        }
        for (String uri : normalized(redirectUris)) {
            builder.redirectUri(uri);
        }
        for (String uri : normalized(postLogoutRedirectUris)) {
            builder.postLogoutRedirectUri(uri);
        }
        for (String scope : normalized(scopes)) {
            builder.scope(scope);
        }
        builder.clientSettings(ClientSettings.builder()
                .requireProofKey(!confidential)
                .requireAuthorizationConsent(false)
                .build());
        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(DEFAULT_ACCESS_TOKEN_TTL)
                .refreshTokenTimeToLive(DEFAULT_REFRESH_TOKEN_TTL)
                .reuseRefreshTokens(false)
                .build());
    }

    private static ClientVO toVO(RegisteredClient client, List<ClientLegalLink> legalLinks) {
        ClientSettings clientSettings = client.getClientSettings();
        TokenSettings tokenSettings = client.getTokenSettings();
        Set<String> authMethods = authMethodsOf(client);
        return new ClientVO(
                client.getId(),
                client.getClientId(),
                client.getClientName(),
                legalLinks.stream()
                        .map(link -> new LegalLinkVO(link.getTitle(), link.getUrl()))
                        .toList(),
                List.copyOf(authMethods),
                values(client.getAuthorizationGrantTypes(), AuthorizationGrantType::getValue),
                new ArrayList<>(client.getRedirectUris()),
                new ArrayList<>(client.getPostLogoutRedirectUris()),
                new ArrayList<>(client.getScopes()),
                clientSettings.isRequireProofKey(),
                clientSettings.isRequireAuthorizationConsent(),
                tokenSettings.getAccessTokenTimeToLive().toMinutes(),
                tokenSettings.getRefreshTokenTimeToLive().toDays(),
                client.getClientIdIssuedAt(),
                isConfidential(authMethods),
                AuthorizationServerConfig.BFF_CLIENT_ID.equals(client.getClientId()));
    }

    private static List<ClientLegalLinkInput> inputsOf(List<LegalLinkReq> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .map(link -> new ClientLegalLinkInput(link.title(), link.url()))
                .toList();
    }

    /** Client authentication methods as their plain string values. */
    private static Set<String> authMethodsOf(RegisteredClient client) {
        return client.getClientAuthenticationMethods().stream()
                .map(ClientAuthenticationMethod::getValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** A client is confidential unless its only authentication method is {@code none}. */
    private static boolean isConfidential(Set<String> authMethods) {
        return !(authMethods.size() == 1 && authMethods.contains(PUBLIC_AUTH_METHOD));
    }

    private static <T> List<String> values(Collection<T> source, Function<T, String> value) {
        return source.stream().map(value).sorted().toList();
    }

    private static Set<String> normalized(List<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                if (StringUtil.isNotBlank(value)) {
                    result.add(value.trim());
                }
            }
        }
        return result;
    }

    /** URL-safe Base64 of {@value #SECRET_BYTES} random bytes, without padding. */
    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
