package cn.nitrowater.server.web.admin;

import cn.nitrowater.server.ai.DocsIndexService;
import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiUsageService;
import cn.nitrowater.server.ai.agent.AgentToolProviders;
import cn.nitrowater.server.ai.moderation.AiModerationService;
import cn.nitrowater.server.ai.skill.SkillRegistry;
import cn.nitrowater.server.ai.tool.WebSearchProvider;
import cn.nitrowater.server.infra.entity.AiModerationHit;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.web.CurrentUser;
import cn.nitrowater.server.web.dto.AiModerationHitResp;
import cn.nitrowater.server.web.dto.AiModerationResp;
import cn.nitrowater.server.web.dto.AiReindexResp;
import cn.nitrowater.server.web.dto.AiAdminSettingsReq;
import cn.nitrowater.server.web.dto.AiSkillResp;
import cn.nitrowater.server.web.dto.AiToolResp;
import cn.nitrowater.server.web.dto.AiUsageResetReq;
import cn.nitrowater.server.web.dto.AiUsageSummaryResp;
import cn.nitrowater.server.web.dto.AiUserUsageResp;
import jakarta.validation.Valid;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.security.Principal;

/**
 * Admin read/operations surface for the documentation assistant.
 *
 * <p>Skills and tools are read-only here: tools are code and skills are files/classpath content,
 * so the console exposes their catalog and status rather than a runtime editor. Index rebuild is
 * explicit because it embeds the whole corpus and costs money/time.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAiController {

    private final DocsIndexService indexService;
    private final SkillRegistry skills;
    private final AgentToolProviders toolProviders;
    private final WebSearchProvider webSearchProvider;
    private final AiAdminSettingsService settings;
    private final AiUsageService usage;
    private final AiModerationService moderation;
    private final BizUserService users;

    public AdminAiController(DocsIndexService indexService, SkillRegistry skills,
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

    /** Admin: list skills with their source. */
    @GetMapping("/api/admin/ai/skills")
    public List<AiSkillResp> listSkills() {
        return skills.list().stream()
                .map(skill -> new AiSkillResp(skill.name(), skill.description(), skill.whenToUse(), skill.source()))
                .toList();
    }

    /**
     * Admin: the tool catalog advertised to the agent.
     *
     * @return core tools plus the optional web tool with its enabled flag and JSON schema
     */
    @GetMapping("/api/admin/ai/tools")
    public List<AiToolResp> listTools() {
        AiAdminSettingsService.Settings runtime = settings.get();
        List<AiToolResp> tools = new ArrayList<>();
        for (ToolCallback callback : toolProviders.core().getToolCallbacks()) {
            tools.add(toTool(callback, "core", runtime.toolEnabled(callback.getToolDefinition().name())));
        }
        for (ToolCallback callback : toolProviders.web().getToolCallbacks()) {
            tools.add(toTool(callback, "web",
                    runtime.toolEnabled(callback.getToolDefinition().name()) && webSearchProvider.enabled()));
        }
        return List.copyOf(tools);
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

    /** Admin: list recorded per-user UTC-day usage, largest consumers first. */
    @GetMapping("/api/admin/ai/usage")
    public List<AiUserUsageResp> usage() {
        List<AiUsageService.UserUsage> rows = usage.allUsersToday();
        Map<Long, BizUser> profiles = users.profiles(rows.stream().map(AiUsageService.UserUsage::uid).toList());
        return rows.stream().map(row -> {
            BizUser profile = profiles.get(row.uid());
            return new AiUserUsageResp(row.uid(),
                    profile == null ? null : profile.getUsername(),
                    profile == null ? null : profile.getNickname(),
                    AiUserUsageResp.Usage.from(row));
        }).toList();
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
     * <p>Paging is keyset by {@code beforeId}: hits arrive continuously, so an offset page would
     * shift under the reviewer between clicks.</p>
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param beforeId return hits with an id lower than this value; {@code null} for the newest page
     * @param limit maximum rows to return
     * @return the moderation overview
     */
    @GetMapping("/api/admin/ai/moderation")
    public AiModerationResp moderation(
            @RequestParam(name = "direction", required = false) ModerationDirection direction,
            @RequestParam(name = "action", required = false) ModerationAction action,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return moderationReport(direction, action, beforeId, limit);
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
     * @param beforeId return hits with an id lower than this value; {@code null} for the newest page
     * @param limit maximum hits to return
     * @return the refreshed moderation overview
     */
    @PostMapping("/api/admin/ai/moderation/reload")
    public AiModerationResp reloadModeration(
            @RequestParam(name = "direction", required = false) ModerationDirection direction,
            @RequestParam(name = "action", required = false) ModerationAction action,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        moderation.reload();
        return moderationReport(direction, action, beforeId, limit);
    }

    private AiModerationResp moderationReport(ModerationDirection direction, ModerationAction action,
            Long beforeId, int limit) {
        AiModerationService.HitPage page = moderation.search(direction, action, beforeId, limit);
        Map<Long, BizUser> profiles = users.profiles(
                page.hits().stream().map(AiModerationHit::getUid).distinct().toList());
        List<AiModerationHitResp> hits = page.hits().stream().map(row -> {
            BizUser profile = profiles.get(row.getUid());
            return new AiModerationHitResp(row.getId(), row.getUid(),
                    profile == null ? null : profile.getUsername(),
                    profile == null ? null : profile.getNickname(),
                    row.getConversationUuid(),
                    row.getDirection().name(), row.getAction().name(),
                    row.getWords(), row.getHitCount(), row.getCreatedAt());
        }).toList();
        return new AiModerationResp(moderation.mode().name(), moderation.dictionarySize(),
                moderation.hitsLast24Hours(), page.total(), page.hasMore(), hits);
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
