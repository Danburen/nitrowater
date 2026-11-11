package cn.nitrowater.server.web.admin;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import cn.nitrowater.server.service.ai.ops.AiAdminReportService;
import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.tool.AiToolCatalogService;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.service.ai.skill.SkillService;
import cn.nitrowater.server.service.ai.model.rag.AiReindexResult;
import cn.nitrowater.server.service.ai.model.skill.SkillExport;
import cn.nitrowater.server.web.CurrentUser;
import cn.nitrowater.server.web.dto.AiAdminSettingsReq;
import cn.nitrowater.server.web.dto.AiSkillReq;
import cn.nitrowater.server.web.dto.AiUsageResetReq;
import cn.nitrowater.server.web.vo.AiModerationResp;
import cn.nitrowater.server.web.vo.AiReindexResp;
import cn.nitrowater.server.web.vo.AiSettingsResp;
import cn.nitrowater.server.web.vo.AiSkillDetailResp;
import cn.nitrowater.server.web.vo.AiSkillResp;
import cn.nitrowater.server.web.vo.AiToolResp;
import cn.nitrowater.server.web.vo.AiUsageSummaryResp;
import cn.nitrowater.server.web.vo.AiUserUsageResp;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * Admin read/operations surface for the documentation assistant.
 *
 * <p>Skills are data: built-ins ship on the classpath, managed rows live in {@code ai_skill} and are
 * created/imported/edited here — saving is the activation step, because the agent reads the table on
 * every turn. Tool toggles and the reporting read models live in {@link AiToolCatalogService} and
 * {@link AiAdminReportService}; this controller only binds HTTP to them.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAiController {

    private final SkillService skills;
    private final AiAdminSettingsService settings;
    private final AiUsageService usage;
    private final AiToolCatalogService toolCatalog;
    private final AiAdminReportService reports;

    public AdminAiController(SkillService skills, AiAdminSettingsService settings,
            AiUsageService usage, AiToolCatalogService toolCatalog, AiAdminReportService reports) {
        this.skills = skills;
        this.settings = settings;
        this.usage = usage;
        this.toolCatalog = toolCatalog;
        this.reports = reports;
    }

    /**
     * Admin: paged skill catalog (built-ins overlaid with managed rows).
     *
     * @param keyword optional name / description / when-to-use substring
     * @param page zero-based page index
     * @param size page size
     * @return one page of catalog rows
     */
    @GetMapping("/api/admin/ai/skills")
    public ApiResponse<Page<AiSkillResp>> listSkills(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(skills.page(keyword, page, size).map(AiSkillResp::from));
    }

    /** Admin: one skill, with its body and provenance. */
    @GetMapping("/api/admin/ai/skills/{name}")
    public ApiResponse<AiSkillDetailResp> getSkill(@PathVariable String name) {
        return ApiResponse.success(AiSkillDetailResp.from(skills.detail(name)));
    }

    /**
     * Admin: download one skill as a {@code SKILL.md} (front matter + body). Built-ins export too,
     * which is the round-trip an operator needs before tweaking and re-importing one.
     */
    @GetMapping("/api/admin/ai/skills/{name}/export")
    public ResponseEntity<byte[]> exportSkill(@PathVariable String name) {
        SkillExport skill = skills.exportMarkdown(name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(skill.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(skill.content().getBytes(StandardCharsets.UTF_8));
    }

    /** Admin: create a managed skill (its name may shadow a built-in). */
    @PostMapping("/api/admin/ai/skills")
    public ApiResponse<AiSkillDetailResp> createSkill(@Valid @RequestBody AiSkillReq request,
            Principal principal) {
        return ApiResponse.success(AiSkillDetailResp.from(
                skills.create(request.toDraft(), CurrentUser.requireUid(principal))));
    }

    /** Admin: update a managed skill. The name is immutable. */
    @PutMapping("/api/admin/ai/skills/{name}")
    public ApiResponse<AiSkillDetailResp> updateSkill(@PathVariable String name,
            @Valid @RequestBody AiSkillReq request, Principal principal) {
        return ApiResponse.success(AiSkillDetailResp.from(
                skills.update(name, request.toDraft(), CurrentUser.requireUid(principal))));
    }

    /** Admin: delete a managed skill, restoring a same-named built-in. */
    @DeleteMapping("/api/admin/ai/skills/{name}")
    public ApiResponse<Void> deleteSkill(@PathVariable String name) {
        skills.delete(name);
        return ApiResponse.success();
    }

    /** Admin: import a {@code SKILL.md} (front matter + body); same-name managed rows are replaced. */
    @PostMapping("/api/admin/ai/skills/import")
    public ApiResponse<AiSkillDetailResp> importSkill(@RequestParam("file") MultipartFile file,
            Principal principal) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded file", ex);
        }
        return ApiResponse.success(AiSkillDetailResp.from(
                skills.importMarkdown(content, file.getOriginalFilename(),
                        CurrentUser.requireUid(principal))));
    }

    /** Admin: the tool catalog advertised to the agent. */
    @GetMapping("/api/admin/ai/tools")
    public List<AiToolResp> listTools() {
        return toolCatalog.list().stream().map(AiToolResp::from).toList();
    }

    /**
     * Admin: show or hide one registered tool for subsequent turns.
     *
     * @param name registered tool name
     * @param enabled whether the model should see it
     * @return the refreshed tool catalog
     */
    @PutMapping("/api/admin/ai/tools/{name}")
    public ApiResponse<List<AiToolResp>> setToolEnabled(@PathVariable String name,
            @RequestParam(defaultValue = "true") boolean enabled) {
        return ApiResponse.success(toolCatalog.setEnabled(name, enabled).stream()
                .map(AiToolResp::from).toList());
    }

    /** Admin: persisted AI features and per-user token limit. */
    @GetMapping("/api/admin/ai/settings")
    public AiSettingsResp getSettings() {
        return AiSettingsResp.from(settings.get());
    }

    /** Admin: update the complete AI runtime settings snapshot. */
    @PutMapping("/api/admin/ai/settings")
    public AiSettingsResp updateSettings(@Valid @RequestBody AiAdminSettingsReq request,
            Principal principal) {
        return AiSettingsResp.from(
                settings.update(request.toSettings(), CurrentUser.requireUid(principal)));
    }

    /**
     * Admin: paged per-user UTC-day usage.
     *
     * @param keyword      optional case-insensitive match on username / nickname, or on uid
     * @param onlyExceeded when {@code true}, keep only users at or above their daily token limit
     * @param sort         ordering: {@code tokens} (default, largest first), {@code tokens_asc},
     *                     {@code requests} (most requests first) or {@code uid}
     * @param page         zero-based page index
     * @param size         page size
     * @return one page of matching usage rows
     */
    @GetMapping("/api/admin/ai/usage")
    public ApiResponse<Page<AiUserUsageResp>> usage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean onlyExceeded,
            @RequestParam(defaultValue = "tokens") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                reports.usagePage(keyword, onlyExceeded, sort, page, size)
                        .map(AiUserUsageResp::from)
        );
    }

    /**
     * Admin: platform-wide usage — today's snapshot plus a per-day series.
     *
     * @param days window length (clamped to 1..366)
     * @return today's totals, the configured ceiling and the series
     */
    @GetMapping("/api/admin/ai/usage/summary")
    public AiUsageSummaryResp usageSummary(@RequestParam(name = "days", defaultValue = "31") int days) {
        return AiUsageSummaryResp.from(
                reports.usageSummary(days)
        );
    }

    /** Admin: clear today's platform-wide counter (the manual "lift the breaker" action). */
    @PostMapping("/api/admin/ai/usage/reset-global")
    public int resetGlobalUsage() {
        return usage.resetGlobalToday();
    }

    /** Admin: reset selected users, or all users when the request list is empty. */
    @PostMapping("/api/admin/ai/usage/reset")
    public int resetUsage(@RequestBody(required = false) AiUsageResetReq request) {
        return usage.resetToday(request == null ? null : request.uids());
    }

    /**
     * Admin: content-moderation posture, dictionary size and a page of hits.
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param keyword optional username / nickname / uid substring
     * @param from inclusive lower bound on the hit time, or {@code null}
     * @param to exclusive upper bound on the hit time, or {@code null}
     * @param page zero-based page index
     * @param size page size
     * @return the moderation overview with a paged hit list
     */
    @GetMapping("/api/admin/ai/moderation")
    public AiModerationResp moderation(
            @RequestParam(name = "direction", required = false) ModerationDirection direction,
            @RequestParam(name = "action", required = false) ModerationAction action,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return AiModerationResp.from(
                reports.moderation(direction, action, keyword, from, to, page, size));
    }

    /**
     * Admin: reload the violation dictionary from its configured locations and return the refreshed
     * report in the same round-trip.
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param keyword optional username / nickname / uid substring
     * @param from inclusive lower bound on the hit time, or {@code null}
     * @param to exclusive upper bound on the hit time, or {@code null}
     * @param page zero-based page index
     * @param size page size
     * @return the refreshed moderation overview
     */
    @PostMapping("/api/admin/ai/moderation/reload")
    public AiModerationResp reloadModeration(
            @RequestParam(name = "direction", required = false) ModerationDirection direction,
            @RequestParam(name = "action", required = false) ModerationAction action,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return AiModerationResp.from(
                reports.reloadModeration(direction, action, keyword, from, to, page, size));
    }

    /**
     * Admin: rebuilds the documentation index (delete corpus, re-embed, upsert).
     *
     * @return the rebuild summary
     */
    @PostMapping("/api/admin/ai/reindex")
    public AiReindexResp reindex() {
        AiReindexResult result = reports.reindex();
        return new AiReindexResp(result.files(), result.chunks(), result.indexedAt(),
                result.durationMs());
    }
}
