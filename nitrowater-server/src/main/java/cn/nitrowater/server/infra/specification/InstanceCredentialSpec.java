package cn.nitrowater.server.infra.specification;

import cn.nitrowater.core.infrastructure.persistence.specification.SpecificationBuilder;
import cn.nitrowater.server.infra.entity.InstanceCredential;
import cn.nitrowater.server.infra.enums.InstanceCredentialStatus;
import org.springframework.data.jpa.domain.Specification;

/** JPA filters for the admin instance-credential listing. */
public final class InstanceCredentialSpec {

    private InstanceCredentialSpec() {
    }

    /**
     * Matches credentials by status and keyword.
     *
     * @param status  optional lifecycle filter
     * @param keyword optional case-insensitive match on kid / prefix / remark
     * @return the combined specification
     */
    public static Specification<InstanceCredential> of(InstanceCredentialStatus status, String keyword) {
        return SpecificationBuilder.<InstanceCredential>builder()
                .eq("status", status)
                .likeIgnoreCaseAny(keyword, "keyId", "keyPrefix", "remark")
                .build();
    }
}
