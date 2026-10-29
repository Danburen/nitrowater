package cn.nitrowater.server.web.admin;

import cn.nitrowater.server.ai.DocsIndexService;
import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.AiUsageService;
import cn.nitrowater.server.ai.agent.AgentToolProviders;
import cn.nitrowater.server.ai.skill.SkillRegistry;
import cn.nitrowater.server.ai.tool.WebSearchProvider;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.service.BizUserService;
import cn.nitrowater.server.web.CurrentUser;
import cn.nitrowater.server.web.dto.AiReindexResp;
import cn.nitrowater.server.web.dto.AiAdminSettingsReq;
import cn.nitrowater.server.web.dto.AiSkillResp;
import cn.nitrowater.server.web.dto.AiToolResp;
import cn.nitrowater.server.web.dto.AiUsageResetReq;
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
    private final BizUserService users;

    public AdminAiController(DocsIndexService indexService, SkillRegistry skills,
            AgentToolProviders toolProviders, WebSearchProvider webSearchProvider,
            AiAdminSettingsService settings, AiUsageService usage, BizUserService users) {
        this.indexService = indexService;
        this.skills = skills;
        this.toolProviders = toolProviders;
        this.webSearchProvider = webSearchProvider;
        this.settings = settings;
        this.usage = usage;
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

    /** Admin: reset selected users, or all users when the request list is empty. */
    @PostMapping("/api/admin/ai/usage/reset")
    public int resetUsage(@RequestBody(required = false) AiUsageResetReq request) {
        return usage.resetToday(request == null ? null : request.uids());
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
