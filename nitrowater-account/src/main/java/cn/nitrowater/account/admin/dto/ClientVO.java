package cn.nitrowater.account.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Application (OIDC client) view for the admin console.
 *
 * <p>Never carries the client secret: the plaintext is returned exactly once by
 * {@link ClientCreatedVO} (create) or {@link ClientSecretVO} (reset) and is not retrievable afterwards
 * because only its hash is persisted.</p>
 *
 * @param id                          internal client id (the {@code oauth2_registered_client.id} PK,
 *                                    not the OAuth2 {@code client_id})
 * @param clientId                    public OAuth2 {@code client_id}
 * @param clientName                  display name
 * @param clientAuthenticationMethods authenticated by secret ({@code client_secret_*}) or {@code none}
 * @param authorizationGrantTypes     grant types the client may use
 * @param redirectUris                registered redirect URIs
 * @param postLogoutRedirectUris      logged-out callback URIs
 * @param scopes                      scopes the client may request
 * @param requireProofKey             PKCE required
 * @param requireAuthorizationConsent consent screen required
 * @param accessTokenTtlMinutes       access-token lifetime in minutes
 * @param refreshTokenTtlDays         refresh-token lifetime in days
 * @param clientIdIssuedAt            when the client was first registered
 * @param confidential                true when the client holds a secret (any non-{@code none} method)
 * @param protectedClient             true for first-party clients that cannot be deleted
 */
public record ClientVO(
        String id,
        String clientId,
        String clientName,
        List<String> clientAuthenticationMethods,
        List<String> authorizationGrantTypes,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        List<String> scopes,
        boolean requireProofKey,
        boolean requireAuthorizationConsent,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays,
        Instant clientIdIssuedAt,
        boolean confidential,
        boolean protectedClient) {
}
