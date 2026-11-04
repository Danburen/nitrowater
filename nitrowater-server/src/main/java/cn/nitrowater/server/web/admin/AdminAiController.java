package cn.nitrowater.server.web.admin;

import cn.nitrowater.server.service.ai.DocsIndexService;
import cn.nitrowater.server.service.ai.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.AiUsageService;
import cn.nitrowater.server.service.ai.SkillService;
import cn.nitrowater.server.service.ai.model.agent.AgentToolProviders;
import cn.nitrowater.server.service.ai.AiModerationService;
import cn.nitrowater.server.service.ai.tool.WebSearchProvider;
import cn.nitrowater.server.infra.entity.AiModerationHit;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.web.CurrentUser;
import cn.nitrowater.server.web.dto.AiModerationHitResp;
import cn.nitrowater.server.web.dto.AiModerationResp;
import cn.nitrowater.server.web.dto.AiReindexResp;
import cn.nitrowater.server.web.dto.AiAdminSettingsReq;
import cn.nitrowater.server.web.dto.AiSkillDetailResp;
import cn.nitrowater.server.web.dto.AiSkillReq;
import cn.nitrowater.server.web.dto.AiSkillResp;
import cn.nitrowater.server.web.dto.AiToolResp;
import cn.nitrowater.server.web.dto.AiUsageResetReq;
import cn.nitrowater.server.web.dto.AiUsageSummaryResp;
import cn.nitrowater.server.web.dto.AiUserUsageResp;
import jakarta.validation.Valid;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Admin read/operations surface for the documentation assistant.
 *
 * <p>Tools are code: the console shows their catalog and schema, and can hide one for subsequent
 * turns, but cannot edit them. Skills are data: built-ins ship on the classpath, managed rows live
 * in {@code ai_skill} and are created/imported/edited here — saving is the activation step, because
 * the agent reads the table on every turn.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAiController {

    private final DocsIndexService indexService;
    private final SkillService skills;
    private final AgentToolProviders toolProviders;
    private final WebSearchProvider webSearchProvider;
    private final AiAdminSettingsService settings;
    private final AiUsageService usage;
    private final AiModerationService moderation;
    private final BizUserService users;

    public AdminAiController(DocsIndexService indexService, SkillService skills,
            AgentToolProviders toolProviders, WebSearchProvider webSearchProvider,
            AiAdminSettingsService settings, AiUsageService usage, AiModerationService moderation,
            BizUserService users) {
        this.indexService = indexService;
        this.skills = skills;
        this.toolProviders = toolProviders;
        this.webSearchProvider = webSearchProvider;
        this.settings = settings;
        this.usage = usage;
        this.moderation = moderation;
        this.users = users;
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
        return ApiResponse.success(skills.page(keyword, page, size));
    }

    /** Admin: one skill, with its body and provenance. */
    @GetMapping("/api/admin/ai/skills/{name}")
    public ApiResponse<AiSkillDetailResp> getSkill(@PathVariable String name) {
        return ApiResponse.success(skills.detail(name));
    }

    /** Admin: create a managed skill (its name may shadow a built-in). */
    @PostMapping("/api/admin/ai/skills")
    public ApiResponse<AiSkillDetailResp> createSkill(@Valid @RequestBody AiSkillReq request,
            Principal principal) {
        return ApiResponse.success(skills.create(request, CurrentUser.requireUid(principal)));
    }

    /** Admin: update a managed skill. The name is immutable. */
    @PutMapping("/api/admin/ai/skills/{name}")
    public ApiResponse<AiSkillDetailResp> updateSkill(@PathVariable String name,
            @Valid @RequestBody AiSkillReq request, Principal principal) {
        return ApiResponse.success(skills.update(name, request, CurrentUser.requireUid(principal)));
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
        return ApiResponse.success(skills.importMarkdown(file, CurrentUser.requireUid(principal)));
    }

    /**
     * Admin: the tool catalog advertised to the agent.
     *
     * @return core tools plus the optional web tool with its enabled flag and JSON schema
     */
    @GetMapping("/api/admin/ai/tools")
    public List<AiToolResp> listTools() {
        return toolResponses();
    }

    /**
     * Admin: show or hide one registered tool for subsequent turns. The callback stays registered,
     * so this is a hot toggle rather than a code hot-load.
     *
     * @param name registered tool name
     * @param enabled whether the model should see it
     * @return the refreshed tool catalog
     */
    @PutMapping("/api/admin/ai/tools/{name}")
    public ApiResponse<List<AiToolResp>> setToolEnabled(@PathVariable String name,
            @RequestParam(defaultValue = "true") boolean enabled) {
        if (!isKnownTool(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tool: " + name);
        }
        settings.setToolEnabled(name, enabled);
        return ApiResponse.success(toolResponses());
    }

    private List<AiToolResp> toolResponses() {
        List<AiToolResp> tools = new ArrayList<>();
        for (ToolCallback callback : toolProviders.core().getToolCallbacks()) {
            tools.add(
                    toTool(
                            callback,
                            "core",
                            settings.toolEnabled(callback.getToolDefinition().name())
                    )
            );
        }
        for (ToolCallback callback : toolProviders.web().getToolCallbacks()) {
            tools.add(
                    toTool(
                            callback,
                            "web",
                    settings.toolEnabled(callback.getToolDefinition().name()) && webSearchProvider.enabled()
                    )
            );
        }
        return List.copyOf(tools);
    }

    private boolean isKnownTool(String name) {
        return Arrays.stream(toolProviders.core().getToolCallbacks())
                .anyMatch(callback -> callback.getToolDefinition().name().equals(name))
                || Arrays.stream(toolProviders.web().getToolCallbacks())
                .anyMatch(callback -> callback.getToolDefinition().name().equals(name));
    }

    /** Admin: persisted AI features and per-user token limit. */
    @GetMapping("/api/admin/ai/settings")
    public AiAdminSettingsService.Settings getSettings() {
        return settings.get();
    }

    /** Admin: update the complete AI runtime settings snapshot. */
    @PutMapping("/api/admin/ai/settings")
    public AiAdminSettingsService.Settings updateSettings(@Valid @RequestBody AiAdminSettingsReq request,
            Principal principal) {
        return settings.update(request.toSettings(), CurrentUser.requireUid(principal));
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
        Page<AiUsageService.UserUsage> rows = usage.pageUsersToday(keyword, onlyExceeded, sort, page, size);
        Map<Long, BizUser> profiles = users.profiles(
                rows.getContent().stream().map(AiUsageService.UserUsage::uid).toList());
        return ApiResponse.success(rows.map(row -> {
            BizUser profile = profiles.get(row.uid());
            return new AiUserUsageResp(row.uid(),
                    profile == null ? null : profile.getUsername(),
                    profile == null ? null : profile.getNickname(),
                    AiUserUsageResp.Usage.from(row));
        }));
    }

    /**
     * Admin: platform-wide usage - today's snapshot plus a per-day series from the global sentinel
     * row. This is what the daily-quota card on the console renders.
     *
     * @param days window length (clamped to 1..366)
     * @return today's totals, the configured ceiling and the series
     */
    @GetMapping("/api/admin/ai/usage/summary")
    public AiUsageSummaryResp usageSummary(@RequestParam(name = "days", defaultValue = "31") int days) {
        AiUsageService.GlobalUsage global = usage.globalToday();
        List<AiUsageSummaryResp.Day> series = usage.dailyTotals(days).stream()
                .map(day -> new AiUsageSummaryResp.Day(day.date(), day.inputTokens(), day.outputTokens(),
                        day.totalTokens(), day.requestCount()))
                .toList();
        return new AiUsageSummaryResp(global.enabled(), global.dailyTokenLimit(), global.totalTokens(),
                global.requestCount(), global.tripped(), global.date(), series);
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
     * <p>Input hits are the ones that also drive the risk-control cooldown; output, memory and
     * profile hits are review material for tuning the dictionary, never an account penalty.</p>
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
        return moderationReport(direction, action, keyword, from, to, page, size);
    }

    /**
     * Admin: reload the violation dictionary from its configured locations.
     *
     * <p>Correcting a false positive must not require a redeploy: add the phrase to the allow list
     * (or drop it from the dictionary) and reload. The refreshed report comes back in the same
     * round-trip so the panel updates without a second request.</p>
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
        moderation.reload();
        return moderationReport(direction, action, keyword, from, to, page, size);
    }

    private AiModerationResp moderationReport(ModerationDirection direction, ModerationAction action,
            String keyword, Instant from, Instant to, int page, int size) {
        Page<AiModerationHit> matched = moderation.search(direction, action, keyword, from, to, page, size);
        Map<Long, BizUser> profiles = users.profiles(
                matched.getContent().stream().map(AiModerationHit::getUid).distinct().toList());
        Page<AiModerationHitResp> hits = matched.map(row -> {
            BizUser profile = profiles.get(row.getUid());
            return new AiModerationHitResp(row.getId(), row.getUid(),
                    profile == null ? null : profile.getUsername(),
                    profile == null ? null : profile.getNickname(),
                    row.getConversationUuid(),
                    row.getDirection().name(), row.getAction().name(),
                    row.getWords(), row.getHitCount(), row.getCreatedAt());
        });
        return new AiModerationResp(moderation.mode().name(), moderation.dictionarySize(),
                moderation.hitsLast24Hours(), hits);
    }

    /**
     * Rebuilds the documentation index (delete corpus, re-embed, upsert).
     *
     * @return the rebuild summary
     * @throws ResponseStatusException 503 when the rebuild failed
     */
    @PostMapping("/api/admin/ai/reindex")
    public AiReindexResp reindex() {
        DocsIndexService.IndexStatus status = indexService.reindex();
        if (!status.indexed()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    status.error() == null ? "Re-index failed" : status.error());
        }
        return new AiReindexResp(status.files(), status.chunks(), status.indexedAt(), status.durationMs());
    }

    private static AiToolResp toTool(ToolCallback callback, String source, boolean enabled) {
        ToolDefinition definition = callback.getToolDefinition();
        return new AiToolResp(definition.name(), definition.description(), source, enabled,
                definition.inputSchema());
    }
}
