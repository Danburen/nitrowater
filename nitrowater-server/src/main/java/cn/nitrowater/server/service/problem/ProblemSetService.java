package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.utils.generator.IdentifierGenerator;
import cn.nitrowater.server.infra.entity.ProblemSet;
import cn.nitrowater.server.infra.repository.ProblemSetItemRepository;
import cn.nitrowater.server.infra.repository.ProblemSetRepository;
import cn.nitrowater.server.web.dto.ProblemSetResp;
import cn.nitrowater.server.web.dto.ProblemSetUpsertReq;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Reads and writes problem sets ("题单"). Membership is edited from the problem side. */
@Service
public class ProblemSetService {

    private final ProblemSetRepository sets;
    private final ProblemSetItemRepository items;
    private final ProblemService problemService;
    private final IdentifierGenerator slugGenerator;

    public ProblemSetService(ProblemSetRepository sets, ProblemSetItemRepository items,
            ProblemService problemService, IdentifierGenerator slugGenerator) {
        this.sets = sets;
        this.items = items;
        this.problemService = problemService;
        this.slugGenerator = slugGenerator;
    }

    /** @return enabled sets ordered by sort order then id */
    @Transactional(readOnly = true)
    public List<ProblemSetResp> listEnabled() {
        return sets.findByEnabledTrueOrderBySortOrderAscIdAsc().stream().map(this::toResp).toList();
    }

    /** @return every set ordered by sort order then id (admin) */
    @Transactional(readOnly = true)
    public List<ProblemSetResp> listAll() {
        return sets.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "id")).stream()
                .map(this::toResp).toList();
    }

    /**
     * @param id set id
     * @return the enabled set
     * @throws ResponseStatusException 404 when missing or disabled
     */
    @Transactional(readOnly = true)
    public ProblemSetResp getEnabled(Long id) {
        ProblemSet set = require(id);
        if (!Boolean.TRUE.equals(set.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem set not found");
        }
        return toResp(set);
    }

    /**
     * @param setId set id
     * @return enabled problems filed under the set, in the set's order
     */
    @Transactional(readOnly = true)
    public List<ProblemSummaryResp> listProblems(Long setId) {
        require(setId);
        return problemService.listEnabledBySet(setId);
    }

    /**
     * @param req create payload
     * @return the created set
     */
    @Transactional
    public ProblemSetResp create(ProblemSetUpsertReq req) {
        ProblemSet set = new ProblemSet();
        apply(set, req);
        set.setSlug(resolveSlug(req));
        return toResp(sets.save(set));
    }

    /**
     * @param id set id
     * @param req update payload
     * @return the updated set
     */
    @Transactional
    public ProblemSetResp update(Long id, ProblemSetUpsertReq req) {
        ProblemSet set = require(id);
        apply(set, req);
        set.setUpdatedAt(Instant.now());
        return toResp(sets.save(set));
    }

    /**
     * Deletes a set; its problem links are removed by the DB cascade.
     *
     * @param id set id
     */
    @Transactional
    public void delete(Long id) {
        require(id);
        sets.deleteById(id);
    }

    /** Admin-supplied slug wins (conflict → 409); otherwise generate from the English name, else the name. */
    private String resolveSlug(ProblemSetUpsertReq req) {
        String slug = req.slug();
        if (slug == null || slug.isBlank()) {
            String source = (req.nameEn() != null && !req.nameEn().isBlank()) ? req.nameEn() : req.name();
            return slugGenerator.generateSlug(source, sets::existsBySlug);
        }
        String provided = slug.trim();
        if (sets.existsBySlug(provided)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "problem set slug already exists: " + provided);
        }
        return provided;
    }

    private ProblemSet require(Long id) {
        return sets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem set not found"));
    }

    private static void apply(ProblemSet set, ProblemSetUpsertReq req) {
        set.setName(req.name());
        set.setNameEn(blankToNull(req.nameEn()));
        set.setDescription(blankToNull(req.description()));
        set.setDescriptionEn(blankToNull(req.descriptionEn()));
        set.setColor(blankToNull(req.color()));
        if (req.sortOrder() != null) {
            set.setSortOrder(req.sortOrder());
        }
        if (req.enabled() != null) {
            set.setEnabled(req.enabled());
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private ProblemSetResp toResp(ProblemSet s) {
        return new ProblemSetResp(s.getId(), s.getSlug(), s.getName(), s.getNameEn(), s.getDescription(),
                s.getDescriptionEn(), s.getColor(), s.getSortOrder(), s.getEnabled(),
                (int) items.countByIdSetId(s.getId()));
    }
}
