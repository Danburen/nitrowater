package cn.nitrowater.server.service;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.infra.entity.Submission;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.enums.SubmissionStatus;
import cn.nitrowater.server.infra.repository.BizUserRepository;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.SubmissionRepository;
import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.web.dto.CreateSubmissionReq;
import cn.nitrowater.server.web.dto.SubmissionCaseResp;
import cn.nitrowater.server.web.dto.SubmissionResp;
import cn.nitrowater.server.service.judge.JudgeQueue;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Creates submissions (enqueue for judging) and reads their state/results. */
@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final BizUserRepository users;
    private final BizUserService bizUserService;
    private final JudgeQueue judgeQueue;
    private final SandboxProperties sandbox;

    public SubmissionService(SubmissionRepository submissions, ProblemRepository problems,
            BizUserRepository users, BizUserService bizUserService, JudgeQueue judgeQueue,
            SandboxProperties sandbox) {
        this.submissions = submissions;
        this.problems = problems;
        this.users = users;
        this.bizUserService = bizUserService;
        this.judgeQueue = judgeQueue;
        this.sandbox = sandbox;
    }

    /**
     * Validates and records a submission, then enqueues it for judging.
     *
     * @param jwt caller's access token
     * @param req submission payload
     * @return the freshly queued submission
     */
    public SubmissionResp create(Jwt jwt, CreateSubmissionReq req) {
        BizUser user = bizUserService.resolve(jwt);
        Problem problem = problems.findById(req.problemId())
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));

        Language language;
        try {
            language = Language.fromId(req.language());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language: " + req.language());
        }
        if (req.code().getBytes(StandardCharsets.UTF_8).length > sandbox.maxCodeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code too large");
        }

        Submission submission = new Submission();
        submission.setUserId(user.getId());
        submission.setUid(user.getUid());
        submission.setProblemId(problem.getId());
        submission.setLanguage(language);
        submission.setIoMode(problem.getIoMode());
        submission.setCode(req.code());
        submission.setStatus(SubmissionStatus.QUEUED);
        Submission saved = submissions.save(submission);

        judgeQueue.enqueue(saved.getId());
        return toResp(saved);
    }

    /**
     * @param jwt caller's access token
     * @param id submission id
     * @return the submission (owner or admin only)
     */
    @Transactional(readOnly = true)
    public SubmissionResp get(Jwt jwt, Long id) {
        Submission submission = submissions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        Long uid = BizUserService.uidOf(jwt);
        if (!uid.equals(submission.getUid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your submission");
        }
        return toResp(submission);
    }

    /**
     * @param jwt caller's access token
     * @return the caller's submissions, newest first
     */
    @Transactional(readOnly = true)
    public List<SubmissionResp> listMine(Jwt jwt) {
        BizUser user = users.findByUid(BizUserService.uidOf(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No submissions yet"));
        return submissions.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(SubmissionService::toResp)
                .toList();
    }

    private static SubmissionResp toResp(Submission s) {
        List<Map<String, Object>> raw = JsonUtil.fromJson(s.getCaseResults(), List.class);
        List<SubmissionCaseResp> cases = new ArrayList<>();
        if (raw != null) {
            for (Map<String, Object> c : raw) {
                cases.add(new SubmissionCaseResp(
                        asInt(c.get("index")),
                        asString(c.get("verdict")),
                        asInt(c.get("timeMs")),
                        asInt(c.get("memoryKb")),
                        asString(c.get("message"))));
            }
        }
        return new SubmissionResp(
                s.getId(),
                s.getProblemId(),
                s.getLanguage().name(),
                s.getStatus().name(),
                s.getVerdict() == null ? null : s.getVerdict().name(),
                s.getPassed(),
                s.getTotal(),
                s.getScore(),
                s.getTimeMs(),
                s.getMemoryKb(),
                cases,
                s.getCompileMessage(),
                s.getCreatedAt(),
                s.getFinishedAt());
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
