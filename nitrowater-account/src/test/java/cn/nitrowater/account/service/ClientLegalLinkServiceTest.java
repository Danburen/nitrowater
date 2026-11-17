package cn.nitrowater.account.service;

import cn.nitrowater.account.infra.ClientLegalLink;
import cn.nitrowater.account.infra.ClientLegalLinkRepository;
import cn.nitrowater.core.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Storage rules for the login-page legal links (order, replace semantics, first-party seeding). */
class ClientLegalLinkServiceTest {

    private final ClientLegalLinkRepository repository = mock(ClientLegalLinkRepository.class);
    private final ClientLegalLinkService service = new ClientLegalLinkService(repository);

    @Test
    void replaceStoresTheListInOrderAndTrimsValues() {
        service.replace("app-a", List.of(
                new ClientLegalLinkInput(" 用户协议 ", " https://a.example/terms "),
                new ClientLegalLinkInput("隐私政策", "https://a.example/privacy")));

        verify(repository).deleteByClientId("app-a");
        ArgumentCaptor<ClientLegalLink> saved = ArgumentCaptor.forClass(ClientLegalLink.class);
        verify(repository, times(2)).save(saved.capture());

        assertThat(saved.getAllValues()).extracting(ClientLegalLink::getClientId)
                .containsOnly("app-a");
        assertThat(saved.getAllValues()).extracting(ClientLegalLink::getTitle)
                .containsExactly("用户协议", "隐私政策");
        assertThat(saved.getAllValues()).extracting(ClientLegalLink::getUrl)
                .containsExactly("https://a.example/terms", "https://a.example/privacy");
        assertThat(saved.getAllValues()).extracting(ClientLegalLink::getSortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void replaceWithAnEmptyListOnlyRemovesTheOldRows() {
        service.replace("app-a", List.of());

        verify(repository).deleteByClientId("app-a");
        verify(repository, never()).save(any());
    }

    @Test
    void replaceRejectsDuplicateTitles() {
        assertThatThrownBy(() -> service.replace("app-a", List.of(
                new ClientLegalLinkInput("用户协议", "https://a.example/terms"),
                new ClientLegalLinkInput(" 用户协议 ", "https://a.example/other"))))
                .isInstanceOf(BizException.class)
                .hasMessage("admin.client.legal_link.duplicate_title");
    }

    @Test
    void ensureAppendsAMissingDocumentAfterTheLastOne() {
        when(repository.findByClientIdOrderBySortOrderAscIdAsc("nitrowater-bff"))
                .thenReturn(List.of(link("用户协议", "https://portal.example/legal/terms", 0)));

        service.ensure("nitrowater-bff", "隐私政策", "https://portal.example/legal/privacy");

        ArgumentCaptor<ClientLegalLink> saved = ArgumentCaptor.forClass(ClientLegalLink.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("隐私政策");
        assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
    }

    @Test
    void ensureReconcilesTheUrlOfAnExistingDocumentWithoutReorderingIt() {
        ClientLegalLink existing = link("用户协议", "https://old.example/terms", 3);
        when(repository.findByClientIdOrderBySortOrderAscIdAsc("nitrowater-bff"))
                .thenReturn(List.of(existing));

        service.ensure("nitrowater-bff", "用户协议", "https://new.example/terms");

        assertThat(existing.getUrl()).isEqualTo("https://new.example/terms");
        assertThat(existing.getSortOrder()).isEqualTo(3);
        verify(repository).save(existing);
        verify(repository, never()).deleteByClientId(any());
    }

    private static ClientLegalLink link(String title, String url, int order) {
        ClientLegalLink link = new ClientLegalLink();
        link.setClientId("nitrowater-bff");
        link.setTitle(title);
        link.setUrl(url);
        link.setSortOrder(order);
        return link;
    }
}
