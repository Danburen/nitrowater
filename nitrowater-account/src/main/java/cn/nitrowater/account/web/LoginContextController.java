package cn.nitrowater.account.web;

import cn.nitrowater.account.config.AuthorizationServerConfig;
import cn.nitrowater.account.infra.ClientLegalLink;
import cn.nitrowater.account.service.ClientLegalLinkService;
import cn.nitrowater.account.web.dto.response.LegalLinkResp;
import cn.nitrowater.account.web.dto.response.LoginContextResp;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.lib.utils.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Context for the hosted auth pages.
 *
 * <p>When an application redirects to {@code /oauth2/authorize}, the request is saved in the
 * session before the login page is shown. This endpoint reports which client that was, so the page
 * can say "you are signing in to X", and returns the legal documents that client registered. It
 * reads only the SSO's own {@code client_legal_link} table: no outbound request is made, and the
 * texts themselves stay on the application's host (Entra ID / Cognito model).</p>
 *
 * <p>A page opened directly (no pending client) is the operator's own login / registration, so it
 * renders the first-party application's documents. If the pending application registered none,
 * the list stays empty instead of borrowing another application's links — linking a user to
 * someone else's terms would be misleading.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginContextController {

    private final PendingAuthorizationRequest pending;
    private final RegisteredClientRepository registeredClientRepository;
    private final ClientLegalLinkService clientLegalLinks;

    /**
     * @param request  current request (holds the session with the saved request)
     * @param response current response, required by the saved-request cache
     * @return the pending application and its legal documents, or the first-party links when
     *         nothing is pending
     */
    @GetMapping("/login-context")
    public ApiResponse<LoginContextResp> loginContext(HttpServletRequest request, HttpServletResponse response) {
        String clientId = this.pending.clientId(request, response);
        if (clientId == null) {
            return ApiResponse.success(new LoginContextResp(
                    null, null, linksOf(AuthorizationServerConfig.BFF_CLIENT_ID)));
        }
        RegisteredClient client = this.registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            // A saved request can outlive the client it names; the page must still render.
            return ApiResponse.success(new LoginContextResp(clientId, clientId, List.of()));
        }
        String clientName = StringUtil.isNotBlank(client.getClientName()) ? client.getClientName() : clientId;
        return ApiResponse.success(new LoginContextResp(clientId, clientName, linksOf(client.getClientId())));
    }

    private List<LegalLinkResp> linksOf(String clientId) {
        return this.clientLegalLinks.findByClientId(clientId).stream()
                .map(LoginContextController::toResp)
                .toList();
    }

    private static LegalLinkResp toResp(ClientLegalLink link) {
        return new LegalLinkResp(link.getTitle(), link.getUrl());
    }
}
