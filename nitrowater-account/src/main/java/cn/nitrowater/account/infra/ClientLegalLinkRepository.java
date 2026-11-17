package cn.nitrowater.account.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Legal-link rows of the SSO database, keyed by the OAuth2 {@code client_id}.
 *
 * <p>The collection is a bounded child list of one client (no keyword filter, no paging), so the
 * derived queries below are the whole contract; a {@code Specification} would add nothing.</p>
 */
public interface ClientLegalLinkRepository extends JpaRepository<ClientLegalLink, Long> {

    /** All links of one application, in display order. */
    List<ClientLegalLink> findByClientIdOrderBySortOrderAscIdAsc(String clientId);

    /** Links of several applications at once, so a client listing does not issue one query per row. */
    List<ClientLegalLink> findByClientIdInOrderByClientIdAscSortOrderAscIdAsc(Collection<String> clientIds);

    /**
     * Removes every link of an application (update / client deletion).
     *
     * <p>A bulk delete (rather than a derived one) executes the DELETE immediately, so a following
     * replace cannot insert a row with the same {@code (client_id, title)} before the old row is
     * gone.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ClientLegalLink link where link.clientId = :clientId")
    int deleteByClientId(@Param("clientId") String clientId);
}
