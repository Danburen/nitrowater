package cn.nitrowater.core.infrastructure.persistence.specification;

import cn.nitrowater.core.domain.role.Role;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the role listing.
 */
public final class RoleSpec {

    private RoleSpec() {
    }

    /**
     * Matches roles by keyword and built-in flag.
     *
     * @param keyword optional code / name / description substring
     * @param builtin optional built-in filter
     * @return the combined specification
     */
    public static Specification<Role> of(String keyword, Boolean builtin) {
        return SpecificationBuilder.<Role>builder()
                .likeIgnoreCaseAny(keyword, "code", "name", "description")
                .eq("builtin", builtin)
                .build();
    }
}
