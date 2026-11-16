package cn.nitrowater.server.infra.repository;

import cn.nitrowater.server.infra.entity.AiDocIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for the documentation index inventory.
 *
 * <p>The table holds a handful of rows per corpus, so plain {@code findAll} / {@code deleteAll} are
 * enough; filtering and paging happen in the service because the report is a merge of these rows
 * with the corpus mount, not a listable table.</p>
 */
public interface AiDocIndexRepository extends JpaRepository<AiDocIndex, Long> {

    /**
     * @param corpus logical corpus name
     * @return the indexed rows of that corpus
     */
    List<AiDocIndex> findByCorpus(String corpus);
}
