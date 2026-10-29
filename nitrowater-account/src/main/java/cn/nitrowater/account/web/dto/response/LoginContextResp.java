package cn.nitrowater.account.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Which application is currently asking for a login.
 *
 * <p>Both fields are {@code null} when the login page was opened directly (no pending OIDC
 * authorization request in the session).</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginContextResp {
    /** OIDC {@code client_id} of the pending authorization request. */
    private String clientId;

    /** Human readable client name shown on the hosted login page. */
    private String clientName;
}
