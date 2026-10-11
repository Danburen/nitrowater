package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.lib.utils.generator.IdentifierGenerator;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.ProblemDifficulty;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.ProblemTestCaseRepository;
import cn.nitrowater.server.web.dto.ProblemDetailResp;
import cn.nitrowater.server.web.dto.ProblemSummaryResp;
import cn.nitrowater.server.web.dto.ProblemUpsertReq;
import cn.nitrowater.server.web.dto.TestCaseResp;
import cn.nitrowater.server.web.dto.TestCaseUpsertReq;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads and writes problems / test cases. */
@Service
public class ProblemService {

    private final ProblemRepository problems;
    private final ProblemTestCaseRepository testCases;
    private final IdentifierGenerator slugGenerator;

    public ProblemService(ProblemRepository problems, ProblemTestCaseRepository testCases,
            IdentifierGenerator slugGenerator) {
        this.problems = problems;
        this.testCases = testCases;
        this.slugGenerator = slugGenerator;
    }

    /** @return all enabled problems as summary views */
    @Transactional(readOnly = true)
    public List<ProblemSummaryResp> listEnabled() {
        return problems.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .map(ProblemService::toSummary)
                .toList();
    }

    /**
     * @param id problem id
     * @return the problem detail (sample test cases only)
     * @throws ResponseStatusException 404 when missing or disabled
     */
    @Transactional(readOnly = true)
    public ProblemDetailResp detail(Long id) {
        Problem problem = require(id);
        List<TestCaseResp> samples = testCases.findByProblemIdOrderByIdxAsc(id).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsSample()))
                .map(c -> new TestCaseResp(c.getIdx(), c.getStdin(), c.getExpectedOutput(), c.getIsSample(), c.getScore()))
                .toList();
        return toDetail(problem, samples);
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
        return toSummary(problems.save(problem));
    }

    /**
     * Resolves the slug for a new problem: an admin-supplied slug wins (conflict → 409), otherwise
     * one is generated from the title and de-duplicated.
     */
    private String resolveSlug(ProblemUpsertReq req) {
        String slug = req.slug();
        if (slug == null || slug.isBlank()) {
            return slugGenerator.generateSlug(req.title(), problems::existsBySlug);
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
        return toSummary(problems.save(problem));
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
        problem.setDifficulty(difficultyOf(req.difficulty()));
        problem.setCategory(req.category());
        problem.setTags(req.tags() == null ? null : JsonUtil.toJson(req.tags()));
        problem.setDescription(req.description());
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

    private static ProblemSummaryResp toSummary(Problem p) {
        return new ProblemSummaryResp(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty().name(),
                p.getCategory(), tagsOf(p), p.getRewardPoints(), p.getTimeLimitMs(), p.getMemoryLimitKb(),
                p.getEnabled());
    }

    private static ProblemDetailResp toDetail(Problem p, List<TestCaseResp> samples) {
        return new ProblemDetailResp(p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty().name(),
                p.getCategory(), tagsOf(p), p.getDescription(), p.getIoMode().name(),
                templatesOf(p), judgeConfigOf(p), p.getTimeLimitMs(), p.getMemoryLimitKb(),
                p.getRewardPoints(), p.getEnabled(), samples);
    }

    private static List<String> tagsOf(Problem p) {
        List<String> tags = JsonUtil.fromJson(p.getTags(), List.class);
        return tags == null ? List.of() : tags;
    }

    private static Map<String, String> templatesOf(Problem p) {
        return JsonUtil.fromJson(p.getTemplates(), Map.class);
    }

    private static Map<String, Object> judgeConfigOf(Problem p) {
        return JsonUtil.fromJson(p.getJudgeConfig(), Map.class);
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
