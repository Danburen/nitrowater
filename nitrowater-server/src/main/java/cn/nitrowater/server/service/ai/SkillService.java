package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.entity.AiSkillEntity;
import cn.nitrowater.server.infra.enums.SkillOrigin;
import cn.nitrowater.server.infra.repository.AiSkillRepository;
import cn.nitrowater.server.service.ai.model.Skill;
import cn.nitrowater.server.web.dto.AiSkillDetailResp;
import cn.nitrowater.server.web.dto.AiSkillReq;
import cn.nitrowater.server.web.dto.AiSkillResp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Write side of the managed skill catalog, plus the admin listing.
 *
 * <p>Saving a skill is the activation step: the read path ({@link SkillRegistry#list()}) queries the
 * table on every call, so there is no cache to invalidate, no reload button and no per-replica
 * divergence. Built-in skills stay read-only on the classpath; writing a row with the same name
 * overrides one, and deleting the row restores it.</p>
 */
@Service
public class SkillService {

    /** Catalog rows per page, clamped so a caller cannot ask for everything. */
    private static final int MAX_PAGE_SIZE = 100;
    /** A skill body is prompt text, not a document store. */
    private static final int MAX_BODY_CHARS = 20_000;
    /** Lowercase kebab-case: the name is referenced verbatim by {@code loadSkill}. */
    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private final AiSkillRepository rows;
    private final SkillRegistry registry;

    public SkillService(AiSkillRepository rows, SkillRegistry registry) {
        this.rows = rows;
        this.registry = registry;
    }

    /**
     * Paged view of the effective catalog (built-ins overlaid with managed rows).
     *
     * <p>The union of a jar resource set and a database table cannot be expressed as one SQL page,
     * and the catalog is bounded by design (a handful of built-ins plus what an operator authored),
     * so the page is sliced from the merged list.</p>
     *
     * @param keyword optional name / description / when-to-use substring
     * @param page zero-based page index
     * @param size page size, clamped to 1..100
     * @return one page of catalog rows
     */
    @Transactional(readOnly = true)
    public Page<AiSkillResp> page(String keyword, int page, int size) {
        Map<String, AiSkillEntity> managed = byName(rows.findAllByOrderByNameAsc());
        Set<String> builtinNames = registry.builtinNames();
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<AiSkillResp> all = new ArrayList<>();
        for (Skill skill : registry.list()) {
            if (!matches(skill, needle)) {
                continue;
            }
            all.add(toRow(skill, managed.get(skill.name()), builtinNames));
        }
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int from = Math.min(all.size(), Math.max(0, page) * safeSize);
        int to = Math.min(all.size(), from + safeSize);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(Math.max(0, page), safeSize), all.size());
    }

    /**
     * @param name skill name (case-insensitive)
     * @return the effective skill with its body and provenance
     * @throws ResponseStatusException 404 when no built-in or managed row matches
     */
    @Transactional(readOnly = true)
    public AiSkillDetailResp detail(String name) {
        Skill skill = registry.get(name);
        if (skill == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown skill: " + name);
        }
        AiSkillEntity row = rows.findByNameIgnoreCase(skill.name()).orElse(null);
        Set<String> builtinNames = registry.builtinNames();
        boolean builtin = builtinNames.contains(skill.name());
        return new AiSkillDetailResp(
                row == null ? null : row.getId(),
                skill.name(), skill.description(), skill.whenToUse(), skill.body(),
                skill.source(),
                row == null ? null : row.getOrigin().name(),
                builtin, builtin && row != null,
                row == null ? null : row.getUpdatedAt());
    }

    /**
     * Creates a managed skill. The name may shadow a built-in (that is the override path).
     *
     * @param req payload
     * @param uid admin uid
     * @return the created skill
     * @throws ResponseStatusException 409 when a managed row with that name already exists
     */
    @Transactional
    public AiSkillDetailResp create(AiSkillReq req, long uid) {
        String name = requireName(req.name());
        if (rows.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill already exists: " + name);
        }
        AiSkillEntity row = new AiSkillEntity();
        row.setName(name);
        row.setOrigin(SkillOrigin.FORM);
        row.setCreatedAt(Instant.now());
        apply(row, req, uid);
        return detail(rows.save(row).getName());
    }

    /**
     * Updates one managed skill. The name is immutable: prompt references and the tool's
     * {@code loadSkill(name)} must keep pointing at the same skill.
     *
     * @param name current name
     * @param req payload; {@code req.name()} must match
     * @param uid admin uid
     * @return the updated skill
     */
    @Transactional
    public AiSkillDetailResp update(String name, AiSkillReq req, long uid) {
        AiSkillEntity row = requireManaged(name);
        if (req.name() != null && !req.name().equalsIgnoreCase(row.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill name is immutable");
        }
        apply(row, req, uid);
        return detail(rows.save(row).getName());
    }

    /**
     * Deletes one managed skill, restoring a same-named built-in.
     *
     * @param name skill name
     */
    @Transactional
    public void delete(String name) {
        rows.delete(requireManaged(name));
    }

    /**
     * Imports a {@code SKILL.md} (front matter + body) as a managed skill, overwriting a same-named
     * managed row. A built-in with the same name ends up overridden.
     *
     * @param file uploaded markdown file
     * @param uid admin uid
     * @return the stored skill
     */
    @Transactional
    public AiSkillDetailResp importMarkdown(MultipartFile file, long uid) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty upload");
        }
        String raw;
        try {
            raw = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        String fallback = fallbackName(file.getOriginalFilename());
        Skill parsed = SkillRegistry.parse(raw, fallback, SkillRegistry.SOURCE_MANAGED);
        String name = requireName(parsed.name());
        if (parsed.body() == null || parsed.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill body is empty");
        }
        if (parsed.body().length() > MAX_BODY_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Skill body exceeds " + MAX_BODY_CHARS + " characters");
        }
        AiSkillEntity row = rows.findByNameIgnoreCase(name).orElseGet(() -> {
            AiSkillEntity created = new AiSkillEntity();
            created.setName(name);
            created.setCreatedAt(Instant.now());
            return created;
        });
        row.setDescription(trimTo(parsed.description(), 255));
        row.setWhenToUse(trimTo(parsed.whenToUse(), 255));
        row.setBody(parsed.body().trim());
        row.setOrigin(SkillOrigin.IMPORT);
        row.setUpdatedBy(uid);
        row.setUpdatedAt(Instant.now());
        return detail(rows.save(row).getName());
    }

    private static AiSkillResp toRow(Skill skill, AiSkillEntity row, Set<String> builtinNames) {
        boolean builtin = builtinNames.contains(skill.name());
        return new AiSkillResp(
                row == null ? null : row.getId(),
                skill.name(), skill.description(), skill.whenToUse(), skill.source(),
                builtin, builtin && row != null,
                row == null ? null : row.getUpdatedAt());
    }

    private static boolean matches(Skill skill, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return contains(skill.name(), needle)
                || contains(skill.description(), needle)
                || contains(skill.whenToUse(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Map<String, AiSkillEntity> byName(List<AiSkillEntity> list) {
        Map<String, AiSkillEntity> map = new LinkedHashMap<>();
        for (AiSkillEntity row : list) {
            map.put(row.getName(), row);
        }
        return map;
    }

    private AiSkillEntity requireManaged(String name) {
        return rows.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No managed skill named " + name));
    }

    private void apply(AiSkillEntity row, AiSkillReq req, long uid) {
        String body = req.body() == null ? "" : req.body().trim();
        if (body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill body is empty");
        }
        if (body.length() > MAX_BODY_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Skill body exceeds " + MAX_BODY_CHARS + " characters");
        }
        row.setDescription(trimTo(req.description(), 255));
        row.setWhenToUse(trimTo(req.whenToUse(), 255));
        row.setBody(body);
        row.setUpdatedBy(uid);
        row.setUpdatedAt(Instant.now());
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Skill name must be lowercase letters, digits and dashes (max 64)");
        }
        return name;
    }

    private static String trimTo(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** @return the uploaded file name without extension, as a name candidate */
    private static String fallbackName(String originalFilename) {
        String name = originalFilename == null ? "skill" : originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name.trim().isEmpty() ? "skill" : name.trim();
    }
}
