package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.lib.utils.generator.IdentifierGenerator;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemSet;
import cn.nitrowater.server.infra.entity.ProblemSetItem;
import cn.nitrowater.server.infra.entity.ProblemSetItemId;
import cn.nitrowater.server.infra.entity.ProblemTag;
import cn.nitrowater.server.infra.entity.ProblemTagId;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.entity.Tag;
import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.ProblemDifficulty;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.ProblemSetItemRepository;
import cn.nitrowater.server.infra.repository.ProblemSetRepository;
import cn.nitrowater.server.infra.repository.ProblemTagRepository;
import cn.nitrowater.server.infra.repository.ProblemTestCaseRepository;
import cn.nitrowater.server.infra.repository.TagRepository;
import cn.nitrowater.server.web.dto.AdminProblemDetailResp;
import cn.nitrowater.server.web.dto.ProblemDetailResp;
import cn.nitrowater.server.web.dto.ProblemSetBriefResp;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import cn.nitrowater.server.web.dto.ProblemUpsertReq;
import cn.nitrowater.server.web.dto.TagResp;
import cn.nitrowater.server.web.dto.TestCaseResp;
import cn.nitrowater.server.web.dto.TestCaseUpsertReq;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads and writes problems / test cases, and keeps their tag / set links in sync. */
@Service
public class ProblemService {

    private final ProblemRepository problems;
    private final ProblemTestCaseRepository testCases;
    private final TagRepository tagRepository;
    private final ProblemTagRepository problemTags;
    private final ProblemSetRepository problemSets;
    private final ProblemSetItemRepository problemSetItems;
    private final IdentifierGenerator slugGenerator;

    public ProblemService(ProblemRepository problems, ProblemTestCaseRepository testCases,
            TagRepository tagRepository, ProblemTagRepository problemTags,
            ProblemSetRepository problemSets, ProblemSetItemRepository problemSetItems,
            IdentifierGenerator slugGenerator) {
        this.problems = problems;
        this.testCases = testCases;
        this.tagRepository = tagRepository;
        this.problemTags = problemTags;
        this.problemSets = problemSets;
        this.problemSetItems = problemSetItems;
        this.slugGenerator = slugGenerator;
    }

    /** @return all enabled problems as summary views */
    @Transactional(readOnly = true)
    public List<ProblemSummaryResp> listEnabled() {
        return problems.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .map(p -> toSummary(p, tagsOf(p.getId()), setsOf(p.getId())))
                .toList();
    }

    /**
     * @param setId problem set id
     * @return enabled problems filed under that set, in the set's order
     */
    @Transactional(readOnly = true)
    public List<ProblemSummaryResp> listEnabledBySet(Long setId) {
        List<Long> problemIds = problemSetItems.findByIdSetIdOrderByOrdAscIdProblemIdAsc(setId).stream()
                .map(item -> item.getId().getProblemId())
                .toList();
        if (problemIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Problem> byId = problems.findAllById(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));
        return problemIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .map(p -> toSummary(p, tagsOf(p.getId()), setsOf(p.getId())))
                .toList();
    }

    /**
     * @param id problem id
     * @return the problem detail (sample test cases only)
     * @throws ResponseStatusException 404 when missing
     */
    @Transactional(readOnly = true)
    public ProblemDetailResp detail(Long id) {
        Problem problem = require(id);
        List<TestCaseResp> samples = testCases.findByProblemIdOrderByIdxAsc(id).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsSample()))
                .map(c -> new TestCaseResp(c.getIdx(), c.getStdin(), c.getExpectedOutput(), c.getIsSample(), c.getScore()))
                .toList();
        return toDetail(problem, tagsOf(id), setsOf(id), samples);
    }

    /** @return all problems (including disabled), ordered by id, as summary views */
    @Transactional(readOnly = true)
    public List<ProblemSummaryResp> listAll() {
        return problems.findAll().stream()
                .sorted(Comparator.comparing(Problem::getId))
                .map(p -> toSummary(p, tagsOf(p.getId()), setsOf(p.getId())))
                .toList();
    }

    /**
     * @param id problem id
     * @return the full admin detail (every test case, hidden ones included)
     * @throws ResponseStatusException 404 when missing
     */
    @Transactional(readOnly = true)
    public AdminProblemDetailResp adminDetail(Long id) {
        Problem problem = require(id);
        List<TestCaseResp> all = testCases.findByProblemIdOrderByIdxAsc(id).stream()
                .map(c -> new TestCaseResp(c.getIdx(), c.getStdin(), c.getExpectedOutput(), c.getIsSample(), c.getScore()))
                .toList();
        return toAdminDetail(problem, tagsOf(id), setsOf(id), all);
    }

    /**
     * @param req create payload
     * @param authorUid creator uid
     * @return the created problem's summary
     */
    @Transactional
    public ProblemSummaryResp create(ProblemUpsertReq req, Long authorUid) {
        Problem problem = new Problem();
        apply(problem, req);
        problem.setSlug(resolveSlug(req));
        problem.setCreatedBy(authorUid);
        Problem saved = problems.save(problem);
        replaceTags(saved.getId(), req.tagIds());
        replaceSets(saved.getId(), req.setIds());
        return toSummary(saved, tagsOf(saved.getId()), setsOf(saved.getId()));
    }

    /**
     * Resolves the slug for a new problem: an admin-supplied slug wins (conflict → 409), otherwise
     * one is generated from the English title when present, falling back to the (possibly Chinese)
     * title, and de-duplicated.
     */
    private String resolveSlug(ProblemUpsertReq req) {
        String slug = req.slug();
        if (slug == null || slug.isBlank()) {
            String source = (req.titleEn() != null && !req.titleEn().isBlank()) ? req.titleEn() : req.title();
            return slugGenerator.generateSlug(source, problems::existsBySlug);
        }
        String provided = slug.trim();
        if (problems.existsBySlug(provided)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug already exists: " + provided);
        }
        return provided;
    }

    /**
     * @param id problem id
     * @param req update payload
     * @return the updated problem's summary
     */
    @Transactional
    public ProblemSummaryResp update(Long id, ProblemUpsertReq req) {
        Problem problem = require(id);
        apply(problem, req);
        problem.setUpdatedAt(Instant.now());
        Problem saved = problems.save(problem);
        replaceTags(id, req.tagIds());
        replaceSets(id, req.setIds());
        return toSummary(saved, tagsOf(id), setsOf(id));
    }

    /**
     * Replaces all test cases of a problem.
     *
     * @param id problem id
     * @param cases new test cases (index must be unique)
     */
    @Transactional
    public void replaceTestCases(Long id, List<TestCaseUpsertReq> cases) {
        require(id);
        testCases.deleteAll(testCases.findByProblemIdOrderByIdxAsc(id));
        List<ProblemTestCase> rows = new ArrayList<>(cases.size());
        for (TestCaseUpsertReq c : cases) {
            ProblemTestCase row = new ProblemTestCase();
            row.setProblemId(id);
            row.setIdx(c.idx());
            row.setStdin(c.stdin());
            row.setExpectedOutput(c.expectedOutput());
            row.setIsSample(Boolean.TRUE.equals(c.isSample()));
            row.setScore(c.score() == null ? 0 : c.score());
            rows.add(row);
        }
        testCases.saveAll(rows);
    }

    private Problem require(Long id) {
        return problems.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));
    }

    private static void apply(Problem problem, ProblemUpsertReq req) {
        problem.setTitle(req.title());
        problem.setTitleEn(blankToNull(req.titleEn()));
        problem.setDifficulty(difficultyOf(req.difficulty()));
        problem.setDescription(req.description());
        problem.setDescriptionEn(blankToNull(req.descriptionEn()));
        problem.setIoMode(ioModeOf(req.ioMode()));
        problem.setTemplates(req.templates() == null ? null : JsonUtil.toJson(req.templates()));
        problem.setJudgeConfig(req.judgeConfig() == null ? null : JsonUtil.toJson(req.judgeConfig()));
        if (req.timeLimitMs() != null) {
            problem.setTimeLimitMs(req.timeLimitMs());
        }
        if (req.memoryLimitKb() != null) {
            problem.setMemoryLimitKb(req.memoryLimitKb());
        }
        if (req.rewardPoints() != null) {
            problem.setRewardPoints(req.rewardPoints());
        }
        if (req.enabled() != null) {
            problem.setEnabled(req.enabled());
        }
    }

    /** Replaces the problem's tag links with {@code tagIds} (validating every id exists). */
    private void replaceTags(Long problemId, List<Long> tagIds) {
        problemTags.deleteByIdProblemId(problemId);
        problemTags.flush();
        List<Long> distinct = distinctIds(tagIds);
        if (distinct.isEmpty()) {
            return;
        }
        requireAllExist(distinct, tagRepository::findAllById, "tag");
        problemTags.saveAll(distinct.stream().map(tagId -> {
            ProblemTag row = new ProblemTag();
            row.setId(new ProblemTagId(problemId, tagId));
            return row;
        }).toList());
    }

    /** Replaces the problem's set membership with {@code setIds} (validating every id exists). */
    private void replaceSets(Long problemId, List<Long> setIds) {
        problemSetItems.deleteByIdProblemId(problemId);
        problemSetItems.flush();
        List<Long> distinct = distinctIds(setIds);
        if (distinct.isEmpty()) {
            return;
        }
        requireAllExist(distinct, problemSets::findAllById, "problem set");
        problemSetItems.saveAll(distinct.stream().map(setId -> {
            ProblemSetItem row = new ProblemSetItem();
            row.setId(new ProblemSetItemId(problemId, setId));
            row.setOrd(0);
            return row;
        }).toList());
    }

    private static List<Long> distinctIds(List<Long> ids) {
        return ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static <T> void requireAllExist(List<Long> ids, Function<List<Long>, List<T>> loader,
            String what) {
        if (loader.apply(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown " + what + " id(s): " + ids);
        }
    }

    private List<TagResp> tagsOf(Long problemId) {
        List<Long> tagIds = problemTags.findByIdProblemId(problemId).stream()
                .map(pt -> pt.getId().getTagId())
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Tag> byId = tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        return tagIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProblemService::toTagResp)
                .toList();
    }

    private List<ProblemSetBriefResp> setsOf(Long problemId) {
        List<Long> setIds = problemSetItems.findByIdProblemId(problemId).stream()
                .map(item -> item.getId().getSetId())
                .toList();
        if (setIds.isEmpty()) {
            return List.of();
        }
        Map<Long, ProblemSet> byId = problemSets.findAllById(setIds).stream()
                .collect(Collectors.toMap(ProblemSet::getId, s -> s));
        return setIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ProblemService::toSetBrief)
                .toList();
    }

    private static TagResp toTagResp(Tag t) {
        return new TagResp(t.getId(), t.getSlug(), t.getName(), t.getNameEn(), t.getKind().name(),
                t.getColor(), t.getEnabled());
    }

    private static ProblemSetBriefResp toSetBrief(ProblemSet s) {
        return new ProblemSetBriefResp(s.getId(), s.getSlug(), s.getName(), s.getNameEn(), s.getColor());
    }

    private static ProblemSummaryResp toSummary(Problem p, List<TagResp> tags, List<ProblemSetBriefResp> sets) {
        return new ProblemSummaryResp(p.getId(), p.getSlug(), p.getTitle(), p.getTitleEn(),
                p.getDifficulty().name(), tags, sets, p.getRewardPoints(), p.getTimeLimitMs(),
                p.getMemoryLimitKb(), p.getEnabled());
    }

    private static ProblemDetailResp toDetail(Problem p, List<TagResp> tags, List<ProblemSetBriefResp> sets,
            List<TestCaseResp> samples) {
        return new ProblemDetailResp(p.getId(), p.getSlug(), p.getTitle(), p.getTitleEn(),
                p.getDifficulty().name(), tags, sets, p.getDescription(), p.getDescriptionEn(),
                p.getIoMode().name(), templatesOf(p), judgeConfigOf(p), p.getTimeLimitMs(),
                p.getMemoryLimitKb(), p.getRewardPoints(), p.getEnabled(), samples);
    }

    private static AdminProblemDetailResp toAdminDetail(Problem p, List<TagResp> tags,
            List<ProblemSetBriefResp> sets, List<TestCaseResp> testCases) {
        return new AdminProblemDetailResp(p.getId(), p.getSlug(), p.getTitle(), p.getTitleEn(),
                p.getDifficulty().name(), tags, sets, p.getDescription(), p.getDescriptionEn(),
                p.getIoMode().name(), templatesOf(p), judgeConfigOf(p), p.getTimeLimitMs(),
                p.getMemoryLimitKb(), p.getRewardPoints(), p.getEnabled(), testCases);
    }

    private static Map<String, String> templatesOf(Problem p) {
        return JsonUtil.fromJson(p.getTemplates(), Map.class);
    }

    private static Map<String, Object> judgeConfigOf(Problem p) {
        return JsonUtil.fromJson(p.getJudgeConfig(), Map.class);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static ProblemDifficulty difficultyOf(String value) {
        if (value == null || value.isBlank()) {
            return ProblemDifficulty.EASY;
        }
        try {
            return ProblemDifficulty.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid difficulty: " + value);
        }
    }

    private static IoMode ioModeOf(String value) {
        if (value == null || value.isBlank()) {
            return IoMode.ACM;
        }
        try {
            return IoMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ioMode: " + value);
        }
    }
}
