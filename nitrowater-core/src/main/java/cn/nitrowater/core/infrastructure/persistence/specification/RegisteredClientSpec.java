package cn.nitrowater.core.infrastructure.persistence.specification;

import cn.nitrowater.core.domain.oauth2.RegisteredClientRow;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the registered-client listing.
 *
 * <p>The row is the read-only projection of Spring Authorization Server's
 * {@code oauth2_registered_client}; hydration into a typed client still goes through the
 * vendor repository.</p>
 */
public final class RegisteredClientSpec {

    private RegisteredClientSpec() {
    }

    /**
     * Matches clients by id or display name.
     *
     * @param keyword optional client_id / client_name substring
     * @return the filter specification
     */
    public static Specification<RegisteredClientRow> of(String keyword) {
        return SpecificationBuilder.<RegisteredClientRow>builder()
                .likeIgnoreCaseAny(keyword, "clientId", "clientName")
                .build();
    }
}
