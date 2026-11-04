package cn.nitrowater.core.domain.oauth2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Read-only JPA mapping of Spring Authorization Server's {@code oauth2_registered_client} table.
 *
 * <p>The table is vendor-owned: the DDL is the upstream one ({@code V1__sso_baseline.sql}) and
 * {@code JdbcRegisteredClientRepository} remains the writer. This entity exists only so the admin
 * console can page/filter the list through a normal JPA query instead of hand-written SQL. Column
 * values that Spring stores as delimited strings / JSON are kept as raw text; callers hydrate the
 * full {@code RegisteredClient} via the repository when they need typed settings.</p>
 *
 * <p>Living under {@code cn.nitrowater.core.domain} keeps it inside the account service's
 * {@code @EntityScan}; server and resource services do not scan that package and therefore never
 * map a table that does not exist in their schemas.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "oauth2_registered_client")
public class RegisteredClientRow {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "client_id_issued_at", nullable = false)
    private Instant clientIdIssuedAt;

    @Column(name = "client_secret", length = 200)
    private String clientSecret;

    @Column(name = "client_secret_expires_at")
    private Instant clientSecretExpiresAt;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    /** Comma-separated client authentication method values. */
    @Column(name = "client_authentication_methods", nullable = false, length = 1000)
    private String clientAuthenticationMethods;

    /** Comma-separated authorization grant type values. */
    @Column(name = "authorization_grant_types", nullable = false, length = 1000)
    private String authorizationGrantTypes;

    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;

    @Column(name = "post_logout_redirect_uris", length = 1000)
    private String postLogoutRedirectUris;

    /** Comma-separated scope values. */
    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes;

    /** Serialized {@code ClientSettings} (Spring's key/value form). */
    @Column(name = "client_settings", nullable = false, length = 2000)
    private String clientSettings;

    /** Serialized {@code TokenSettings} (Spring's key/value form). */
    @Column(name = "token_settings", nullable = false, length = 2000)
    private String tokenSettings;
}
