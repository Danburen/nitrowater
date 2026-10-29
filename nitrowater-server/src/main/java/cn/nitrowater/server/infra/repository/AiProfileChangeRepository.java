package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiProfileChange;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@link AiProfileChange}. */
public interface AiProfileChangeRepository extends JpaRepository<AiProfileChange, Long> {

    /**
     * @param uid owner uid
     * @param limit page size
     * @return the most recent changes for that user
     */
    List<AiProfileChange> findByUidOrderByCreatedAtDesc(Long uid, Limit limit);
}
