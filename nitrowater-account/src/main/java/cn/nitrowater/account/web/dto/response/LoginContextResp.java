package cn.nitrowater.account.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Everything the hosted auth pages need to know about the current login.
 *
 * <p>The application fields are {@code null} when the page was opened directly (no pending OIDC
 * authorization request in the session). {@code legalLinks} comes from the SSO-owned
 * {@code client_legal_link} table: each application registers the documents it publishes, and the
 * page renders them in order. The documents themselves stay on the application's host.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginContextResp {
    /** OIDC {@code client_id} of the pending authorization request. */
    private String clientId;

    /** Human readable client name shown on the hosted login page. */
    private String clientName;

    /** Documents of that application, in display order; empty when it registered none. */
    private List<LegalLinkResp> legalLinks;
}
