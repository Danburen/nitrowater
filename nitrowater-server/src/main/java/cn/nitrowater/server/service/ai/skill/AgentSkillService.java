package cn.nitrowater.server.service.ai.skill;

import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.infra.exception.SkillUnknownException;
import cn.nitrowater.server.infra.exception.SkillsDisabledException;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turn-time policy over the skill catalog.
 *
 * <p>{@link SkillRegistry} is the read model (built-ins overlaid with managed rows) and deliberately
 * knows nothing about switches or rejections. This service owns the two decisions a turn needs:</p>
 * <ul>
 *   <li>the catalog offered to the model, empty when the administrator switched skills off;</li>
 *   <li>the explicit selection from the request, resolved and de-duplicated, or a coded rejection.</li>
 * </ul>
 *
 * <p>It is stateless and owns no storage, so it stays a policy layer instead of becoming a second
 * catalog. The admin write side lives in {@link SkillService}.</p>
 */
@Service
public class AgentSkillService {

    private final SkillRegistry registry;

    public AgentSkillService(SkillRegistry registry) {
        this.registry = registry;
    }

    /**
     * Skills the model may discover this turn.
     *
     * @param runtime current admin settings
     * @return the catalog, or an empty list when skills are switched off
     */
    public List<Skill> catalog(AiSettings runtime) {
        return runtime.skillsEnabled() ? registry.list() : List.of();
    }

    /**
     * Resolves the skills the user selected for one turn.
     *
     * <p>Names are trimmed and matched case-insensitively; duplicates (including two spellings of
     * the same skill) keep their first position. An empty selection means "let the agent choose",
     * which is the normal path: the system prompt still lists the catalog and the model loads what
     * it needs through {@code loadSkill}.</p>
     *
     * @param names selected skill names, possibly empty
     * @param runtime current admin settings
     * @return the resolved skills in selection order, never {@code null}
     * @throws SkillsDisabledException when skills are switched off but the caller selected one
     * @throws SkillUnknownException   when a name matches no built-in or managed skill
     */
    public List<Skill> resolveSelected(List<String> names, AiSettings runtime) {
        List<String> wanted = names == null ? List.of() : names.stream()
                .filter(StringUtil::isNotBlank)
                .map(String::trim)
                .toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        if (!runtime.skillsEnabled()) {
            throw new SkillsDisabledException();
        }
        Map<String, Skill> resolved = new LinkedHashMap<>();
        for (String name : wanted) {
            Skill skill = registry.get(name);
            if (skill == null) {
                throw new SkillUnknownException(name);
            }
            resolved.putIfAbsent(skill.name(), skill);
        }
        return List.copyOf(resolved.values());
    }
}
