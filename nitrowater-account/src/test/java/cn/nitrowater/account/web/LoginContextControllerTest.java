package cn.nitrowater.account.web;

import cn.nitrowater.account.config.AuthorizationServerConfig;
import cn.nitrowater.account.infra.ClientLegalLink;
import cn.nitrowater.account.service.ClientLegalLinkService;
import cn.nitrowater.account.web.dto.response.LegalLinkResp;
import cn.nitrowater.account.web.dto.response.LoginContextResp;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards what the hosted pages render: the client that started the login, plus the documents that
 * client registered in {@code client_legal_link}. The SSO never substitutes another application's
 * documents.
 */
class LoginContextControllerTest {

    @Test
    void reportsTheApplicationAndItsRegisteredDocumentsInOrder() {
        LoginContextResp context = controller(client("app-a", "Portal A"), "app-a",
                link("app-a", "用户协议", "https://a.example/terms", 0),
                link("app-a", "隐私政策", "https://a.example/privacy", 1),
                link("app-a", "退款政策", "https://a.example/refund", 2))
                .loginContext(null, null)
                .getData();

        assertThat(context.getClientId()).isEqualTo("app-a");
        assertThat(context.getClientName()).isEqualTo("Portal A");
        assertThat(context.getLegalLinks())
                .extracting(LegalLinkResp::title)
                .containsExactly("用户协议", "隐私政策", "退款政策");
        assertThat(context.getLegalLinks())
                .extracting(LegalLinkResp::url)
                .containsExactly("https://a.example/terms", "https://a.example/privacy",
                        "https://a.example/refund");
    }

    @Test
    void doesNotBorrowAnotherApplicationsDocuments() {
        LoginContextResp context = controller(client("app-b", "Portal B"), "app-b")
                .loginContext(null, null)
                .getData();

        assertThat(context.getClientName()).isEqualTo("Portal B");
        assertThat(context.getLegalLinks()).isEmpty();
    }

    @Test
    void usesTheFirstPartyLinksWhenThePageWasOpenedDirectly() {
        LoginContextResp context = controller(null, AuthorizationServerConfig.BFF_CLIENT_ID,
                link(AuthorizationServerConfig.BFF_CLIENT_ID, "用户协议", "https://portal.example/legal/terms", 0),
                link(AuthorizationServerConfig.BFF_CLIENT_ID, "隐私政策", "https://portal.example/legal/privacy", 1))
                .loginContext(null, null)
                .getData();

        assertThat(context.getClientId()).isNull();
        assertThat(context.getClientName()).isNull();
        assertThat(context.getLegalLinks())
                .extracting(LegalLinkResp::title)
                .containsExactly("用户协议", "隐私政策");
    }

    @Test
    void survivesASavedRequestThatOutlivedItsClient() {
        LoginContextResp context = controllerWithMissingClient("app-gone")
                .loginContext(null, null)
                .getData();

        assertThat(context.getClientId()).isEqualTo("app-gone");
        assertThat(context.getClientName()).isEqualTo("app-gone");
        assertThat(context.getLegalLinks()).isEmpty();
    }

    // ------------------------------------------------------------------ fixtures

    private static LoginContextController controller(RegisteredClient client, String linkedClientId,
            ClientLegalLink... links) {
        PendingAuthorizationRequest pending = mock(PendingAuthorizationRequest.class);
        // any() (not any(Class)) so a null request/response still matches.
        when(pending.clientId(any(), any())).thenReturn(client == null ? null : client.getClientId());

        RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
        if (client != null) {
            when(clients.findByClientId(client.getClientId())).thenReturn(client);
        }

        ClientLegalLinkService legalLinks = mock(ClientLegalLinkService.class);
        when(legalLinks.findByClientId(linkedClientId)).thenReturn(List.of(links));
        return new LoginContextController(pending, clients, legalLinks);
    }

    private static LoginContextController controllerWithMissingClient(String clientId) {
        PendingAuthorizationRequest pending = mock(PendingAuthorizationRequest.class);
        when(pending.clientId(any(), any())).thenReturn(clientId);
        return new LoginContextController(pending, mock(RegisteredClientRepository.class),
                mock(ClientLegalLinkService.class));
    }

    private static RegisteredClient client(String clientId, String name) {
        return RegisteredClient.withId("id-" + clientId)
                .clientId(clientId)
                .clientName(name)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://" + clientId + ".example/login/oauth2/code/nitrowater")
                .clientSettings(ClientSettings.builder().requireProofKey(false).build())
                .build();
    }

    private static ClientLegalLink link(String clientId, String title, String url, int order) {
        ClientLegalLink link = new ClientLegalLink();
        link.setClientId(clientId);
        link.setTitle(title);
        link.setUrl(url);
        link.setSortOrder(order);
        return link;
    }
}
