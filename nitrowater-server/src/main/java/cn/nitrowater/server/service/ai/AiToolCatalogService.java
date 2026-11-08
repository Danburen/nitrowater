package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.service.ai.model.AiSettings;
import cn.nitrowater.server.service.ai.model.tool.AiToolInfo;
import cn.nitrowater.server.service.ai.model.agent.AgentTool;
import cn.nitrowater.server.service.ai.tool.AgentToolRegistry;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Read model and hot switches of the tool catalog advertised to the agent.
 *
 * <p>Tools are code, not data: the console can only show their schema and hide one for subsequent
 * turns. The callback stays registered, so toggling is not a code hot-load.</p>
 *
 * <p>The catalog reports each gate separately ({@code enabled} / {@code optIn} / {@code available})
 * and lets {@link AgentTool} answer them, so adding a gate is one change in the model instead of
 * one more boolean re-derived at every call site.</p>
 */
@Service
public class AiToolCatalogService {

    private final AgentToolRegistry registry;
    private final AiAdminSettingsService settings;

    public AiToolCatalogService(AgentToolRegistry registry, AiAdminSettingsService settings) {
        this.registry = registry;
        this.settings = settings;
    }

    /**
     * @return every registered tool with its gates, catalog bucket and JSON schema
     */
    public List<AiToolInfo> list() {
        AiSettings runtime = settings.get();
        Set<String> disabled = settings.disabledTools();
        List<AiToolInfo> tools = new ArrayList<>(registry.all().size());
        for (AgentTool tool : registry.all()) {
            ToolDefinition definition = tool.callback().getToolDefinition();
            tools.add(new AiToolInfo(
                    definition.name(),
                    definition.description(),
                    tool.feature().bucket(),
                    tool.enabled(runtime, disabled),
                    tool.optIn(),
                    tool.available(),
                    definition.inputSchema()));
        }
        return List.copyOf(tools);
    }

    /**
     * Shows or hides one registered tool for subsequent turns.
     *
     * @param name registered tool name
     * @param enabled whether the model should see it
     * @return the refreshed tool catalog
     * @throws ResponseStatusException 404 when the name is not a registered tool
     */
    public List<AiToolInfo> setEnabled(String name, boolean enabled) {
        if (registry.find(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tool: " + name);
        }
        settings.setToolEnabled(name, enabled);
        return list();
    }
}
