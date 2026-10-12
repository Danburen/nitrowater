package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.utils.generator.IdentifierGenerator;
import cn.nitrowater.server.infra.entity.Tag;
import cn.nitrowater.server.infra.enums.TagKind;
import cn.nitrowater.server.infra.repository.TagRepository;
import cn.nitrowater.server.web.dto.TagResp;
import cn.nitrowater.server.web.dto.TagUpsertReq;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Reads and writes tags. */
@Service
public class TagService {

    private final TagRepository tags;
    private final IdentifierGenerator slugGenerator;

    public TagService(TagRepository tags, IdentifierGenerator slugGenerator) {
        this.tags = tags;
        this.slugGenerator = slugGenerator;
    }

    /** @return enabled tags ordered by kind then id */
    @Transactional(readOnly = true)
    public List<TagResp> listEnabled() {
        return tags.findByEnabledTrueOrderByKindAscIdAsc().stream().map(TagService::toResp).toList();
    }

    /** @return every tag ordered by kind then id (admin) */
    @Transactional(readOnly = true)
    public List<TagResp> listAll() {
        return tags.findAll(Sort.by(Sort.Direction.ASC, "kind", "id")).stream()
                .map(TagService::toResp).toList();
    }

    /**
     * @param req create payload
     * @return the created tag
     */
    @Transactional
    public TagResp create(TagUpsertReq req) {
        Tag tag = new Tag();
        apply(tag, req);
        tag.setSlug(resolveSlug(req));
        return toResp(tags.save(tag));
    }

    /**
     * @param id tag id
     * @param req update payload
     * @return the updated tag
     */
    @Transactional
    public TagResp update(Long id, TagUpsertReq req) {
        Tag tag = require(id);
        apply(tag, req);
        tag.setUpdatedAt(Instant.now());
        return toResp(tags.save(tag));
    }

    /**
     * Deletes a tag; its problem links are removed by the DB cascade.
     *
     * @param id tag id
     */
    @Transactional
    public void delete(Long id) {
        require(id);
        tags.deleteById(id);
    }

    /** Admin-supplied slug wins (conflict → 409); otherwise generate from the English name, else the name. */
    private String resolveSlug(TagUpsertReq req) {
        String slug = req.slug();
        if (slug == null || slug.isBlank()) {
            String source = (req.nameEn() != null && !req.nameEn().isBlank()) ? req.nameEn() : req.name();
            return slugGenerator.generateSlug(source, tags::existsBySlug);
        }
        String provided = slug.trim();
        if (tags.existsBySlug(provided)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tag slug already exists: " + provided);
        }
        return provided;
    }

    private Tag require(Long id) {
        return tags.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found"));
    }

    private static void apply(Tag tag, TagUpsertReq req) {
        tag.setName(req.name());
        tag.setNameEn(blankToNull(req.nameEn()));
        tag.setKind(kindOf(req.kind()));
        tag.setColor(blankToNull(req.color()));
        if (req.enabled() != null) {
            tag.setEnabled(req.enabled());
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static TagKind kindOf(String value) {
        if (value == null || value.isBlank()) {
            return TagKind.TOPIC;
        }
        try {
            return TagKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid kind: " + value);
        }
    }

    private static TagResp toResp(Tag t) {
        return new TagResp(t.getId(), t.getSlug(), t.getName(), t.getNameEn(), t.getKind().name(),
                t.getColor(), t.getEnabled());
    }
}
