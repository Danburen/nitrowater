package cn.nitrowater.account.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create-application payload for the admin console.
 *
 * <p>The confidential secret is never supplied by the caller: it is generated server-side and
 * returned once (see {@link ClientRegistrationVO}). Which authentication methods make the client
 * confidential is derived from {@code clientAuthenticationMethods}.</p>
 *
 * @param clientId                    unique OAuth2 {@code client_id}
 * @param clientName                  display name
 * @param legalLinks                  documents this application publishes (titles + absolute URLs),
 *                                    rendered in order on the login page; empty = none
 * @param clientAuthenticationMethods e.g. {@code client_secret_basic}, {@code client_secret_post}, {@code none}
 * @param authorizationGrantTypes     e.g. {@code authorization_code}, {@code refresh_token}, {@code client_credentials}
 * @param redirectUris                redirect URIs (required by the authorization_code grant)
 * @param postLogoutRedirectUris      RP-initiated logout back-channels
 * @param scopes                      requestable scopes (e.g. {@code openid}, {@code profile})
 *
 * <p>PKCE, the consent page and the token lifetimes are not part of the payload: they are derived
 * (PKCE is required for public clients, no consent page) or fixed (15 min / 30 days), so an operator
 * never gets to configure a lifetime that the seeder would silently revert.</p>
 */
public record ClientCreateReq(
        @NotBlank(message = "client id is required")
        @Size(max = 100, message = "client id must be at most 100 characters")
        String clientId,

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
