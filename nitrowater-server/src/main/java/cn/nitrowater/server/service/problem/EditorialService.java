package cn.nitrowater.server.service.problem;

import cn.nitrowater.server.infra.entity.ProblemEditorial;
import cn.nitrowater.server.infra.enums.EditorialAuthorType;
import cn.nitrowater.server.infra.enums.EditorialStatus;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.repository.ProblemEditorialRepository;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.web.dto.EditorialDetailResp;
import cn.nitrowater.server.web.dto.EditorialSummaryResp;
import cn.nitrowater.server.web.dto.EditorialUpsertReq;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Reads and writes problem editorials (LeetCode Solutions style). */
@Service
public class EditorialService {

    private final ProblemEditorialRepository editorials;
    private final ProblemRepository problems;

    public EditorialService(ProblemEditorialRepository editorials, ProblemRepository problems) {
        this.editorials = editorials;
        this.problems = problems;
    }

    /**
     * Public: published editorials of a problem.
     *
     * @param problemId owning problem id
     * @param sort {@code hot} (default; pinned, likes, newest) or {@code new} (pinned, newest)
     * @return published editorials in display order
     */
    @Transactional(readOnly = true)
    public List<EditorialSummaryResp> listPublished(Long problemId, String sort) {
        requireProblem(problemId);
        List<ProblemEditorial> rows = editorials
                .findByProblemIdAndStatusOrderByPinnedDescLikeCountDescPublishedAtDesc(problemId,
                        EditorialStatus.PUBLISHED);
        return order(rows, sort).stream().map(EditorialService::toSummary).toList();
    }

    /**
     * Public: one published editorial; increments its view count.
     *
     * @param id editorial id
     * @return the editorial detail
     * @throws ResponseStatusException 404 when missing or not published
     */
    @Transactional
    public EditorialDetailResp getPublished(Long id) {
        ProblemEditorial editorial = require(id);
        if (editorial.getStatus() != EditorialStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Editorial not found");
        }
        editorial.setViewCount(editorial.getViewCount() == null ? 1 : editorial.getViewCount() + 1);
        return toDetail(editorials.save(editorial));
    }

    /**
     * Admin: every editorial of a problem regardless of status.
     *
     * @param problemId owning problem id
     * @return all editorials in display order
     */
    @Transactional(readOnly = true)
    public List<EditorialSummaryResp> listAll(Long problemId) {
        requireProblem(problemId);
        return editorials.findByProblemIdOrderByPinnedDescLikeCountDescPublishedAtDesc(problemId).stream()
                .map(EditorialService::toSummary).toList();
    }

    /**
     * @param problemId owning problem id
     * @param req create payload
     * @param authorUid admin uid
     * @return the created editorial
     */
    @Transactional
    public EditorialDetailResp create(Long problemId, EditorialUpsertReq req, Long authorUid) {
        requireProblem(problemId);
        ProblemEditorial editorial = new ProblemEditorial();
        editorial.setProblemId(problemId);
        editorial.setAuthorUid(authorUid);
        editorial.setAuthorType(EditorialAuthorType.OFFICIAL);
        apply(editorial, req);
        return toDetail(editorials.save(editorial));
    }

    /**
     * @param id editorial id
     * @param req update payload
     * @return the updated editorial
     */
    @Transactional
    public EditorialDetailResp update(Long id, EditorialUpsertReq req) {
        ProblemEditorial editorial = require(id);
        apply(editorial, req);
        editorial.setUpdatedAt(Instant.now());
        return toDetail(editorials.save(editorial));
    }

    /**
     * @param id editorial id
     */
    @Transactional
    public void delete(Long id) {
        require(id);
        editorials.deleteById(id);
    }

    /**
     * @param id editorial id
     * @param status new status (sets {@code publishedAt} when first published)
     * @return the updated editorial
     */
    @Transactional
    public EditorialDetailResp changeStatus(Long id, EditorialStatus status) {
        ProblemEditorial editorial = require(id);
        if (status == EditorialStatus.PUBLISHED && editorial.getPublishedAt() == null) {
            editorial.setPublishedAt(Instant.now());
        }
        editorial.setStatus(status);
        editorial.setUpdatedAt(Instant.now());
        return toDetail(editorials.save(editorial));
    }

    /**
     * @param id editorial id
     * @param pinned new pinned flag
     * @return the updated editorial
     */
    @Transactional
    public EditorialDetailResp setPinned(Long id, boolean pinned) {
        ProblemEditorial editorial = require(id);
        editorial.setPinned(pinned);
        editorial.setUpdatedAt(Instant.now());
        return toDetail(editorials.save(editorial));
    }

    private static void apply(ProblemEditorial editorial, EditorialUpsertReq req) {
        editorial.setTitle(req.title());
        editorial.setContent(req.content());
        editorial.setLanguage(languageOf(req.language()));
        if (req.status() != null && !req.status().isBlank()) {
            EditorialStatus status = statusOf(req.status());
            if (status == EditorialStatus.PUBLISHED && editorial.getPublishedAt() == null) {
                editorial.setPublishedAt(Instant.now());
            }
            editorial.setStatus(status);
        }
        if (req.pinned() != null) {
            editorial.setPinned(req.pinned());
        }
    }

    /** {@code new}: pinned first, then newest. {@code hot} (default): already sorted by the repo. */
    private static List<ProblemEditorial> order(List<ProblemEditorial> rows, String sort) {
        if ("new".equalsIgnoreCase(sort == null ? "" : sort.trim())) {
            return rows.stream()
                    .sorted(Comparator
                            .comparingInt((ProblemEditorial e) -> Boolean.TRUE.equals(e.getPinned()) ? 0 : 1)
                            .thenComparing(ProblemEditorial::getPublishedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        return rows;
    }

    private ProblemEditorial require(Long id) {
        return editorials.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Editorial not found"));
    }

    private void requireProblem(Long problemId) {
        if (!problems.existsById(problemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found");
        }
    }

    private static EditorialSummaryResp toSummary(ProblemEditorial e) {
        return new EditorialSummaryResp(e.getId(), e.getProblemId(), e.getAuthorType().name(), e.getTitle(),
                languageId(e), e.getStatus().name(), e.getPinned(), e.getLikeCount(), e.getViewCount(),
                e.getPublishedAt(), e.getCreatedAt());
    }

    private static EditorialDetailResp toDetail(ProblemEditorial e) {
        return new EditorialDetailResp(e.getId(), e.getProblemId(), e.getAuthorUid(), e.getAuthorType().name(),
                e.getTitle(), e.getContent(), languageId(e), e.getStatus().name(), e.getPinned(),
                e.getLikeCount(), e.getViewCount(), e.getPublishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static String languageId(ProblemEditorial e) {
        return e.getLanguage() == null ? null : e.getLanguage().name().toLowerCase();
    }

    private static Language languageOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Language.fromId(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid language: " + id);
        }
    }

    private static EditorialStatus statusOf(String value) {
        try {
            return EditorialStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + value);
        }
    }
}
