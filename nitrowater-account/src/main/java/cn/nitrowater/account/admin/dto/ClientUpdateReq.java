package cn.nitrowater.account.admin.dto;

import jakarta.validation.Valid;
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
 * @param legalLinks                  documents this application publishes; the list replaces the
 *                                    stored one (order preserved), empty = remove all
 * @param clientAuthenticationMethods e.g. {@code client_secret_basic}, {@code client_secret_post}, {@code none}
 * @param authorizationGrantTypes     e.g. {@code authorization_code}, {@code refresh_token}, {@code client_credentials}
 * @param redirectUris                redirect URIs (required by the authorization_code grant)
 * @param postLogoutRedirectUris      RP-initiated logout back-channels
 * @param scopes                      requestable scopes (e.g. {@code openid}, {@code profile})
 *
 * <p>See {@link ClientCreateReq}: PKCE, consent and token lifetimes are derived/fixed and are not
 * accepted from the caller.</p>
 */
public record ClientUpdateReq(
        @NotBlank(message = "client name is required")
        @Size(max = 200, message = "client name must be at most 200 characters")
        String clientName,

        @Size(max = 20, message = "at most 20 legal links are allowed")
        @Valid
        List<LegalLinkReq> legalLinks,

        @NotEmpty(message = "at least one client authentication method is required")
        List<@NotBlank String> clientAuthenticationMethods,

        @NotEmpty(message = "at least one authorization grant type is required")
        List<@NotBlank String> authorizationGrantTypes,

        @NotEmpty(message = "at least one redirect uri is required")
        List<String> redirectUris,

        List<String> postLogoutRedirectUris,

        @NotEmpty(message = "at least one scope is required")
        List<@NotBlank String> scopes) {
}
