package cn.nitrowater.account.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Update-application payload for the admin console.
 *
 * <p>All collections are <em>replaced</em>, not merged. The {@code client_id} is immutable and the
 * secret is preserved (use the reset-secret endpoint to rotate it).</p>
 *
 * @param clientName                  display name
 * @param clientAuthenticationMethods e.g. {@code client_secret_basic}, {@code client_secret_post}, {@code none}
 * @param authorizationGrantTypes     e.g. {@code authorization_code}, {@code refresh_token}, {@code client_credentials}
 * @param redirectUris                redirect URIs (required by the authorization_code grant)
 * @param postLogoutRedirectUris      RP-initiated logout back-channels
 * @param scopes                      requestable scopes (e.g. {@code openid}, {@code profile})
 * @param requireProofKey             PKCE; defaults to true for public clients, false otherwise
 * @param requireAuthorizationConsent whether the consent screen is shown; defaults to false
 * @param accessTokenTtlMinutes       access-token lifetime in minutes; defaults to 15
 * @param refreshTokenTtlDays         refresh-token lifetime in days; defaults to 30
 */
public record ClientUpdateReq(
        @NotBlank(message = "client name is required")
        @Size(max = 200, message = "client name must be at most 200 characters")
        String clientName,

        @NotEmpty(message = "at least one client authentication method is required")
        List<@NotBlank String> clientAuthenticationMethods,

        @NotEmpty(message = "at least one authorization grant type is required")
        List<@NotBlank String> authorizationGrantTypes,

        @NotEmpty(message = "at least one redirect uri is required")
        List<String> redirectUris,

        List<String> postLogoutRedirectUris,

        @NotEmpty(message = "at least one scope is required")
        List<@NotBlank String> scopes,

        Boolean requireProofKey,

        Boolean requireAuthorizationConsent,

        Integer accessTokenTtlMinutes,

        Integer refreshTokenTtlDays) {
}
