package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.server.infra.entity.AiMemory;
import cn.nitrowater.server.infra.enums.MemoryKind;
import cn.nitrowater.server.infra.enums.MemoryStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA filters for the memory listing.
 */
public final class MemorySpec {

    private MemorySpec() {
    }

    /**
     * Matches one user's active memories by kind and content keyword.
     *
     * @param uid owner uid
     * @param kind optional memory category
     * @param keyword optional content substring
     * @return the combined specification
     */
    public static Specification<AiMemory> of(long uid, MemoryKind kind, String keyword) {
        return SpecificationBuilder.<AiMemory>builder()
                .eq("uid", uid)
                .eq("status", MemoryStatus.ACTIVE)
                .eq("kind", kind)
                .likeIgnoreCase("content", keyword)
                .build();
    }
}
