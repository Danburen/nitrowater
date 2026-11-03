package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.service.ai.model.Skill;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Discovers skills from two sources:
 *
 * <ol>
 *   <li><b>built-in</b> — {@code classpath*:ai/skills/<name>/SKILL.md} (shipped in the jar);</li>
 *   <li><b>external</b> — {@code <app.ai.skills-dir>/<name>/SKILL.md} (no rebuild needed;
 *       an external skill overrides a same-named built-in).</li>
 * </ol>
 *
 * <p>The directory is scanned per call; the corpus is tiny and a fresh list keeps editing a skill
 * file immediately effective.</p>
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);
    private static final String CLASSPATH_PATTERN = "classpath*:ai/skills/*/SKILL.md";
    private static final String SKILL_FILE = "SKILL.md";

    private final AiProperties properties;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public SkillRegistry(AiProperties properties) {
        this.properties = properties;
    }

    /** Logs the discovered catalog once, which also proves classpath scanning inside the boot jar. */
    @EventListener(ApplicationReadyEvent.class)
    public void logCatalog() {
        List<Skill> found = list();
        log.info("AI skills ({}): {}", found.size(), found.stream().map(Skill::name).toList());
    }

    /** @return every skill, sorted by name (external overriding built-ins of the same name) */
    public List<Skill> list() {
        Map<String, Skill> byName = new LinkedHashMap<>();
        loadBuiltins(byName);
        loadExternal(byName);
        List<Skill> skills = new ArrayList<>(byName.values());
        skills.sort(Comparator.comparing(Skill::name));
        return List.copyOf(skills);
    }

    /**
     * @param name skill name (case-insensitive, trimmed)
     * @return the skill or {@code null}
     */
    public Skill get(String name) {
        if (StringUtil.isBlank(name)) {
            return null;
        }
        String wanted = name.trim();
        return list().stream()
                .filter(skill -> skill.name().equalsIgnoreCase(wanted))
                .findFirst()
                .orElse(null);
    }

    private void loadBuiltins(Map<String, Skill> byName) {
        try {
            for (Resource resource : resolver.getResources(CLASSPATH_PATTERN)) {
                String raw = resource.getContentAsString(StandardCharsets.UTF_8);
                String fallback = folderName(resource);
                Skill skill = parse(raw, fallback, "builtin");
                byName.put(skill.name(), skill);
            }
        } catch (IOException ex) {
            log.warn("Could not read built-in skills: {}", ex.getMessage());
        }
    }

    private void loadExternal(Map<String, Skill> byName) {
        if (StringUtil.isBlank(properties.skillsDir())) {
            return;
        }
        Path root = Path.of(properties.skillsDir());
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root, 2)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(SKILL_FILE))
                    .sorted()
                    .forEach(path -> {
                        try {
                            String raw = Files.readString(path, StandardCharsets.UTF_8);
                            String fallback = path.getParent().getFileName().toString();
                            Skill skill = parse(raw, fallback, "external");
                            byName.put(skill.name(), skill);
                        } catch (IOException ex) {
                            log.warn("Could not read skill {}: {}", path, ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            log.warn("Could not scan skills dir {}: {}", root, ex.getMessage());
        }
    }

    /**
     * Parses a skill file: optional YAML-ish front matter ({@code name}, {@code description},
     * {@code when_to_use}) followed by the instruction body.
     *
     * @param raw file contents
     * @param fallbackName directory name
     * @param source builtin/external
     * @return the parsed skill
     */
    static Skill parse(String raw, String fallbackName, String source) {
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
