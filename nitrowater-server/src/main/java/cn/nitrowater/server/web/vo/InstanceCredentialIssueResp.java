package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.service.instance.InstanceCredentialService.IssuedCredential;

/**
 * The one-time issuance result: the masked credential plus the plaintext key, shown only once.
 *
 * @param credential masked credential view
 * @param apiKey     full plaintext key ({@code nw_live_...}) — never persisted, shown only in this response
 */
public record InstanceCredentialIssueResp(
        InstanceCredentialResp credential,
        String apiKey) {

    /**
     * @param issued service issuance result
     * @return the wire view carrying the one-time plaintext key
     */
    public static InstanceCredentialIssueResp from(IssuedCredential issued) {
        return new InstanceCredentialIssueResp(
                InstanceCredentialResp.from(issued.credential()),
                issued.rawKey());
    }
}
