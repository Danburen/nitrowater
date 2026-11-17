package cn.nitrowater.account.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One legal document link registered by an OIDC application.
 *
 * <p>Rows are keyed by the OAuth2 {@code client_id} (not the vendor table's numeric id) so the
 * admin console and the hosted login page can look them up with the value the browser already
 * carries. Each application may register any number of documents — terms of service, privacy
 * statement, refund policy, … — and the SSO renders them in {@code sortOrder}.</p>
 *
 * <p>Only the link lives here. The document itself stays on the application's own host; this
 * service never fetches or proxies the text.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "client_legal_link")
public class ClientLegalLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** {@code oauth2_registered_client.client_id} this link belongs to. */
    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    /** Label shown on the login page, e.g. {@code 用户协议}. */
    @Column(name = "title", nullable = false, length = 64)
    private String title;

    /** Absolute URL of the application's own document page. */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** Display order within a client, ascending. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
