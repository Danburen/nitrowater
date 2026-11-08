package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.entity.AiSkillEntity;
import cn.nitrowater.server.infra.repository.AiSkillRepository;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read model of the agent's skill catalog.
 *
 * <p>Two sources, merged by name:</p>
 * <ol>
 *   <li><b>built-in</b> — {@code classpath*:ai/skills/<name>/SKILL.md}, shipped inside the jar and
 *       therefore immutable at runtime (read once);</li>
 *   <li><b>managed</b> — rows in {@code ai_skill}, written by the admin console.</li>
 * </ol>
 *
 * <p>A managed row wins over a same-named built-in, and deleting it restores the built-in. The
 * managed side is read from the database on every call: the table holds a handful of rows, and a
 * live read is what makes "save = live" true on every replica without a reload step or a cache.</p>
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private static final String CLASSPATH_PATTERN = "classpath*:ai/skills/*/SKILL.md";
    private static final String SKILL_FILE = "SKILL.md";
    /** Source label of a classpath skill. */
    public static final String SOURCE_BUILTIN = "builtin";
    /** Source label of a database-managed skill. */
    public static final String SOURCE_MANAGED = "managed";

    private final AiSkillRepository repository;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /** Built-ins never change while the process runs, so one lazy scan is enough. */
    private volatile List<Skill> builtins;

    public SkillRegistry(AiSkillRepository repository) {
        this.repository = repository;
    }

    /** Logs the discovered catalog once, which also proves classpath scanning inside the boot jar. */
    @EventListener(ApplicationReadyEvent.class)
    public void logCatalog() {
        List<Skill> found = list();
        log.info("AI skills ({}): {}", found.size(), found.stream().map(Skill::name).toList());
    }

    /** @return the effective catalog: built-ins overlaid with managed rows, sorted by name */
    public List<Skill> list() {
        Map<String, Skill> merged = new LinkedHashMap<>();
        for (Skill builtin : builtins()) {
            merged.put(builtin.name(), builtin);
        }
        for (AiSkillEntity row : repository.findAllByOrderByNameAsc()) {
            merged.put(row.getName(), toSkill(row));
        }
        List<Skill> skills = new ArrayList<>(merged.values());
        skills.sort(Comparator.comparing(Skill::name));
        return List.copyOf(skills);
    }

    /**
     * @param name skill name (case-insensitive, trimmed)
     * @return the skill or {@code null}
     */
    public Skill get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim();
        return list().stream()
                .filter(skill -> skill.name().equalsIgnoreCase(wanted))
                .findFirst()
                .orElse(null);
    }

    /** @return the built-in skill names, used to mark managed rows that shadow one */
    public Set<String> builtinNames() {
        return builtins().stream().map(Skill::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Parses a skill file: optional YAML-ish front matter ({@code name}, {@code description},
     * {@code when_to_use}) followed by the instruction body.
     *
     * @param raw file contents
     * @param fallbackName name used when the front matter has none (file or directory name)
     * @param source builtin/managed label
     * @return the parsed skill
     */
    public static Skill parse(String raw, String fallbackName, String source) {
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        String normalized = text.replace("\r\n", "\n");
        String name = fallbackName;
        String description = "";
        String whenToUse = "";
        String body = normalized.trim();

        if (normalized.startsWith("---\n")) {
            int end = normalized.indexOf("\n---", 4);
            if (end > 0) {
                String header = normalized.substring(4, end);
                for (String line : header.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    String value = unquote(line.substring(colon + 1).trim());
                    switch (key) {
                        case "name" -> name = value;
                        case "description" -> description = value;
                        case "when_to_use", "when-to-use" -> whenToUse = value;
                        default -> {
                            // Unknown front-matter keys are ignored.
                        }
                    }
                }
                int bodyStart = normalized.indexOf('\n', end + 1);
                body = bodyStart >= 0 ? normalized.substring(bodyStart + 1).trim() : "";
            }
        }

        return new Skill(name.isBlank() ? fallbackName : name, description, whenToUse, body, source);
    }

    /** @return the built-in skills, sorted by name (immutable for the process lifetime) */
    public List<Skill> builtins() {
        List<Skill> current = builtins;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (builtins == null) {
                builtins = loadBuiltins();
            }
            return builtins;
        }
    }

    private List<Skill> loadBuiltins() {
        Map<String, Skill> byName = new LinkedHashMap<>();
        try {
            for (Resource resource : resolver.getResources(CLASSPATH_PATTERN)) {
                String raw = resource.getContentAsString(StandardCharsets.UTF_8);
                String fallback = folderName(resource);
                Skill skill = parse(raw, fallback, SOURCE_BUILTIN);
                byName.put(skill.name(), skill);
            }
        } catch (IOException ex) {
            log.warn("Could not read built-in skills: {}", ex.getMessage());
        }
        return List.copyOf(byName.values());
    }

    private static Skill toSkill(AiSkillEntity row) {
        return new Skill(row.getName(), row.getDescription(), row.getWhenToUse(), row.getBody(),
                SOURCE_MANAGED);
    }

    private static String folderName(Resource resource) {
        try {
            String path = resource.getURL().getPath();
            String[] parts = path.split("/");
            return parts.length >= 2 ? parts[parts.length - 2] : "skill";
        } catch (IOException ex) {
            return "skill";
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
