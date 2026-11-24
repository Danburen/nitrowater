package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.server.infra.entity.InstanceRegistry;
import org.springframework.data.jpa.domain.Specification;

/** JPA filters for the admin instance-registry listing. */
public final class InstanceRegistrySpec {

    private InstanceRegistrySpec() {
    }

    /**
     * Matches live instances of a credential by keyword.
     *
     * @param keyId   credential kid (required)
     * @param keyword optional case-insensitive match on display name / instance id / boot id
     * @return the combined specification
     */
    public static Specification<InstanceRegistry> of(String keyId, String keyword) {
        return SpecificationBuilder.<InstanceRegistry>builder()
                .eq("keyId", keyId)
                .where((root, query, cb) -> cb.isNull(root.get("removedAt")))
                .likeIgnoreCaseAny(keyword, "displayName", "instanceId", "bootId")
                .build();
    }
}
