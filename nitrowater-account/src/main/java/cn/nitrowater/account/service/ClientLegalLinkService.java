package cn.nitrowater.account.service;

import cn.nitrowater.account.infra.ClientLegalLink;
import cn.nitrowater.account.infra.ClientLegalLinkRepository;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Storage for the legal links the hosted login page renders.
 *
 * <p>The rows are owned by the SSO, but the documents are not: each application registers the
 * URLs of documents it hosts itself ({@code 用户协议}, {@code 隐私政策}, and any extra policy it
 * publishes). Rendering therefore needs one local table read — never an outbound request, so a
 * broken application cannot slow down or break the login page.</p>
 */
@Service
@RequiredArgsConstructor
public class ClientLegalLinkService {

    private final ClientLegalLinkRepository links;

    /**
     * @param clientId OAuth2 {@code client_id}
     * @return the application's links in display order; empty when it registered none
     */
    @Transactional(readOnly = true)
    public List<ClientLegalLink> findByClientId(String clientId) {
        if (StringUtil.isBlank(clientId)) {
            return List.of();
        }
        return links.findByClientIdOrderBySortOrderAscIdAsc(clientId);
    }

    /**
     * Batch lookup for the admin client list, grouped by {@code client_id}.
     *
     * @param clientIds OAuth2 client ids of the current page
     * @return links per client; clients without links are absent from the map
     */
    @Transactional(readOnly = true)
    public Map<String, List<ClientLegalLink>> findByClientIds(Collection<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Map.of();
        }
        return links.findByClientIdInOrderByClientIdAscSortOrderAscIdAsc(clientIds).stream()
                .collect(Collectors.groupingBy(ClientLegalLink::getClientId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Replaces an application's links with exactly the supplied list (order preserved).
     *
     * @param clientId OAuth2 {@code client_id}
     * @param items    new links; blank titles/URLs are rejected, duplicate titles too
     */
    @Transactional
    public void replace(String clientId, List<ClientLegalLinkInput> items) {
        links.deleteByClientId(clientId);
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> titles = new HashSet<>();
        int order = 0;
        for (ClientLegalLinkInput item : items) {
            String title = item == null || item.title() == null ? "" : item.title().strip();
            String url = item == null || item.url() == null ? "" : item.url().strip();
            if (StringUtil.isBlank(title) || StringUtil.isBlank(url)) {
                throw new BizException(BaseResponseCode.VALIDATION_ERROR);
            }
            if (!titles.add(title)) {
                throw new BizException(BaseResponseCode.CLIENT_LEGAL_LINK_DUPLICATE_TITLE, title);
            }
            save(clientId, title, url, order++);
        }
    }

    /** Deletes every link of an application (client deletion). */
    @Transactional
    public void deleteByClientId(String clientId) {
        links.deleteByClientId(clientId);
    }

    /**
     * Inserts a link if the application has none with that title, and keeps its URL in sync.
     *
     * <p>Used by the first-party client seeder: {@code 用户协议} / {@code 隐私政策} follow the
     * configured portal URL on every start, while extra documents an administrator added through
     * the console are left untouched.</p>
     *
     * @param clientId OAuth2 {@code client_id}
     * @param title    link label
     * @param url      absolute URL of the document
     */
    @Transactional
    public void ensure(String clientId, String title, String url) {
        if (StringUtil.isBlank(clientId) || StringUtil.isBlank(title) || StringUtil.isBlank(url)) {
            return;
        }
        String trimmedTitle = title.strip();
        String trimmedUrl = url.strip();
        List<ClientLegalLink> existing = links.findByClientIdOrderBySortOrderAscIdAsc(clientId);
        for (ClientLegalLink link : existing) {
            if (trimmedTitle.equals(link.getTitle())) {
                if (!trimmedUrl.equals(link.getUrl())) {
                    link.setUrl(trimmedUrl);
                    links.save(link);
                }
                return;
            }
        }
        int order = existing.stream().mapToInt(ClientLegalLink::getSortOrder).max().orElse(-1) + 1;
        save(clientId, trimmedTitle, trimmedUrl, order);
    }

    private void save(String clientId, String title, String url, int sortOrder) {
        ClientLegalLink link = new ClientLegalLink();
        link.setClientId(clientId);
        link.setTitle(title);
        link.setUrl(url);
        link.setSortOrder(sortOrder);
        links.save(link);
    }
}
