package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.utils.generator.IdentifierGenerator;
import cn.nitrowater.server.infra.entity.ProblemSet;
import cn.nitrowater.server.infra.entity.ProblemSetItem;
import cn.nitrowater.server.infra.entity.ProblemSetItemId;
import cn.nitrowater.server.infra.enums.ProblemSetVisibility;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.ProblemSetItemRepository;
import cn.nitrowater.server.infra.repository.ProblemSetRepository;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.web.dto.ProblemSetResp;
import cn.nitrowater.server.web.dto.ProblemSetUpsertReq;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import cn.nitrowater.server.web.dto.UserProblemSetUpsertReq;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads and writes problem sets ("题单"). Membership is edited from the problem side. */
@Service
public class ProblemSetService {

    private final ProblemSetRepository sets;
    private final ProblemSetItemRepository items;
    private final ProblemRepository problems;
    private final ProblemService problemService;
    private final IdentifierGenerator slugGenerator;

    public ProblemSetService(ProblemSetRepository sets, ProblemSetItemRepository items,
            ProblemRepository problems, ProblemService problemService, IdentifierGenerator slugGenerator) {
        this.sets = sets;
        this.items = items;
        this.problems = problems;
        this.problemService = problemService;
        this.slugGenerator = slugGenerator;
    }

    /** @return enabled sets ordered by sort order then id */
    @Transactional(readOnly = true)
    public List<ProblemSetResp> listEnabled() {
        return sets.findByOwnerUidIsNullAndEnabledTrueOrderBySortOrderAscIdAsc().stream().map(this::toResp).toList();
    }

    /** @return every set ordered by sort order then id (admin) */
    @Transactional(readOnly = true)
    public List<ProblemSetResp> listAll() {
        return sets.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "id")).stream()
                .map(this::toResp).toList();
    }

    /**
     * Anonymous read of a set that is publicly visible: an enabled curated (owner-less) set, or an
     * enabled user-owned set whose visibility is {@code PUBLIC}.
     *
     * @param id set id
     * @return the set
     * @throws ResponseStatusException 404 when missing, disabled, or not publicly visible
     */
    @Transactional(readOnly = true)
    public ProblemSetResp getEnabled(Long id) {
        ProblemSet set = require(id);
        boolean curated = set.getOwnerUid() == null;
        boolean publicUserSet = !curated && set.getVisibility() == ProblemSetVisibility.PUBLIC;
        if (!Boolean.TRUE.equals(set.getEnabled()) || !(curated || publicUserSet)) {
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

    // ---- user-owned sets ("我的题单") ----

    /** @return the caller's enabled problem sets, most recently updated first */
    @Transactional(readOnly = true)
    public List<ProblemSetResp> listMine(Jwt jwt) {
        return sets.findByOwnerUidAndEnabledTrueOrderByUpdatedAtDescIdDesc(BizUserService.uidOf(jwt))
                .stream().map(this::toResp).toList();
    }

    /**
     * @param jwt caller's access token
     * @param id set id
     * @return the caller's own set (owner only)
     * @throws ResponseStatusException 403 when the set is not owned by the caller
     */
    @Transactional(readOnly = true)
    public ProblemSetResp getMine(Jwt jwt, Long id) {
        return toResp(requireOwned(jwt, id));
    }

    /** @return enabled PUBLIC user-owned sets (题单广场), most recently updated first */
    @Transactional(readOnly = true)
    public List<ProblemSetResp> listPublicUserSets() {
        return sets.findByOwnerUidIsNotNullAndVisibilityAndEnabledTrueOrderByUpdatedAtDescIdDesc(
                        ProblemSetVisibility.PUBLIC)
                .stream().map(this::toResp).toList();
    }

    /**
     * @param jwt caller's access token
     * @param req create payload
     * @return the created user-owned set (owner = caller)
     */
    @Transactional
    public ProblemSetResp createForUser(Jwt jwt, UserProblemSetUpsertReq req) {
        ProblemSet set = new ProblemSet();
        set.setOwnerUid(BizUserService.uidOf(jwt));
        set.setSlug(null);
        set.setName(req.name());
        set.setDescription(blankToNull(req.description()));
        set.setVisibility(req.visibility() == null ? ProblemSetVisibility.PRIVATE : req.visibility());
        set.setEnabled(true);
        set.setSortOrder(0);
        return toResp(sets.save(set));
    }

    /**
     * @param jwt caller's access token
     * @param id set id
     * @param req update payload
     * @return the updated set (owner only)
     */
    @Transactional
    public ProblemSetResp updateForUser(Jwt jwt, Long id, UserProblemSetUpsertReq req) {
        ProblemSet set = requireOwned(jwt, id);
        set.setName(req.name());
        set.setDescription(blankToNull(req.description()));
        if (req.visibility() != null) {
            set.setVisibility(req.visibility());
        }
        set.setUpdatedAt(Instant.now());
        return toResp(sets.save(set));
    }

    /**
     * Deletes a user-owned set (owner only); its items are removed by the DB cascade.
     *
     * @param jwt caller's access token
     * @param id set id
     */
    @Transactional
    public void deleteForUser(Jwt jwt, Long id) {
        ProblemSet set = requireOwned(jwt, id);
        sets.deleteById(set.getId());
    }

    /**
     * Replaces a user-owned set's ordered membership with {@code orderedProblemIds} (owner only).
     *
     * @param jwt caller's access token
     * @param id set id
     * @param orderedProblemIds problem ids in display order
     * @return the updated set
     */
    @Transactional
    public ProblemSetResp replaceItems(Jwt jwt, Long id, List<Long> orderedProblemIds) {
        ProblemSet set = requireOwned(jwt, id);
        List<Long> distinct = orderedProblemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty() && problems.findAllById(distinct).size() != distinct.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown problem id in set");
        }
        items.deleteByIdSetId(set.getId());
        items.flush();
        List<ProblemSetItem> rows = new ArrayList<>();
        int ord = 0;
        for (Long problemId : distinct) {
            ProblemSetItem row = new ProblemSetItem();
            row.setId(new ProblemSetItemId(problemId, set.getId()));
            row.setOrd(ord++);
            rows.add(row);
        }
        items.saveAll(rows);
        set.setUpdatedAt(Instant.now());
        return toResp(sets.save(set));
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

    /** Resolves a set owned by the caller, or 403 when it is curated or someone else's. */
    private ProblemSet requireOwned(Jwt jwt, Long id) {
        ProblemSet set = require(id);
        Long uid = BizUserService.uidOf(jwt);
        if (set.getOwnerUid() == null || !set.getOwnerUid().equals(uid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your problem set");
        }
        return set;
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
                (int) items.countByIdSetId(s.getId()), s.getOwnerUid(), s.getVisibility());
    }
}
