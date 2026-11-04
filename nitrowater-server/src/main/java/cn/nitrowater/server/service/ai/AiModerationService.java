package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.infra.exception.ContentBlockedException;
import cn.nitrowater.server.infra.exception.ContentCooldownException;
import cn.nitrowater.server.service.ai.model.ModerationDictionary;
import cn.nitrowater.server.service.ai.model.OutputRedactor;
import cn.nitrowater.server.service.ai.model.PromptLeakGuard;
import cn.nitrowater.server.service.ai.properties.ModerationProperties;
import cn.nitrowater.server.infra.entity.AiModerationHit;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.infra.repository.AiModerationHitRepository;
import cn.nitrowater.server.infra.specification.AiModerationSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Content moderation for NitroBot: the input gate, the answer screen and the memory gate.
 *
 * <p>This is risk control, not a security boundary. A dictionary cannot stop a determined user
 * (homophones, spacing, other languages, code), so the design goal is narrower: keep flagged text
 * out of {@code ai_message} / {@code ai_memory}, leave an audit trail, and pause the AI endpoints
 * for repeat offenders instead of banning accounts.</p>
 *
 * <ul>
 *   <li><b>Input</b> — a flagged question is rejected before the turn is persisted, so the
 *       conversation never grows a row that cannot be deleted.</li>
 *   <li><b>Output</b> — a flagged answer is masked before it reaches MySQL <em>and</em> the
 *       response; tokens were already produced, so this is damage control, not savings.</li>
 *   <li><b>Memory / profile</b> — flagged text the user (or the model) wants stored is refused,
 *       because stored memories and profile preferences are re-injected into every later turn.</li>
 * </ul>
 *
 * <p>Only input hits feed the risk-control counter. Output and stored-text hits are frequently the
 * model's own drift or text quoted from documents and web results, which is not the user's doing.</p>
 *
 * <p>The dictionary is loaded once and can be reloaded from the admin console
 * ({@link #reload()}), so correcting a false positive does not require a redeploy.</p>
 */
@Service
public class AiModerationService {

    private static final Logger LOG = LoggerFactory.getLogger(AiModerationService.class);
    private static final String STRIKE_PREFIX = "nitrowater:ai:guard:strike:";
    private static final String COOLDOWN_PREFIX = "nitrowater:ai:guard:cooldown:";
    private static final int MAX_REPORT_ROWS = 200;

    private final AiAdminSettingsService settings;
    private final AiModerationHitRepository hits;
    private final ModerationProperties properties;
    private final StringRedisTemplate redis;
    private final TransactionTemplate audit;
    private volatile ModerationDictionary dictionary;

    public AiModerationService(AiAdminSettingsService settings, AiModerationHitRepository hits,
            ModerationProperties properties, StringRedisTemplate redis,
            PlatformTransactionManager transactions) {
        this.settings = settings;
        this.hits = hits;
        this.properties = properties;
        this.redis = redis;
        this.dictionary = ModerationDictionary.load(properties.dictLocations(), properties.allowLocations());
        this.audit = new TransactionTemplate(transactions);
        // A hit row must survive the rollback of the rejected turn it belongs to.
        this.audit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return number of violation words currently loaded */
    public int dictionarySize() {
        return dictionary.size();
    }

    /**
     * Reloads the dictionary from its configured locations.
     *
     * <p>Same trick as a config reload: the word list lives outside the jar, so correcting a false
     * positive must not require a full redeploy. Swap the reference instead of mutating, so an
     * in-flight request keeps using the dictionary it started with.</p>
     */
    public synchronized void reload() {
        ModerationDictionary reloaded = ModerationDictionary.load(
                properties.dictLocations(), properties.allowLocations());
        this.dictionary = reloaded;
        LOG.info("NitroBot content moderation dictionary reloaded: {} words", reloaded.size());
    }

    /**
     * @param text arbitrary text
     * @return whether the dictionary matches, regardless of the active posture
     */
    public boolean flagged(String text) {
        return !dictionary.find(text).isEmpty();
    }

    /**
     * @return hits recorded in the last 24 hours (all directions)
     */
    public long hitsLast24Hours() {
        return hits.countByCreatedAtAfter(Instant.now().minus(Duration.ofHours(24)));
    }

    /** Logs the effective posture once the context is up, so ops can see it without the console. */
    @EventListener(ApplicationReadyEvent.class)
    public void logStartup() {
        try {
            LOG.info("NitroBot content moderation ready: mode={}, words={}", mode(), dictionary.size());
        } catch (RuntimeException ex) {
            LOG.warn("NitroBot content moderation could not read its posture: {}", ex.toString());
        }
    }

    /**
     * @return the effective posture ({@link ModerationMode#OFF} when the settings row is missing)
     */
    public ModerationMode mode() {
        ModerationMode mode = settings.get().moderationMode();
        return mode == null ? ModerationMode.OFF : mode;
    }

    /**
     * Rejects a question that matches the dictionary.
     *
     * <p>Called before the turn is stored or sent to the model, so a rejected question costs no
     * tokens and leaves no {@code ai_message} row.</p>
     *
     * @param uid owner uid
     * @param conversationUuid conversation being continued, or {@code null} when starting one
     * @param text sanitized question
     * @throws ContentCooldownException when the user is paused by risk control
     * @throws ContentBlockedException when the question matches the dictionary
     */
    public void guardQuestion(long uid, String conversationUuid, String text) {
        ModerationMode mode = mode();
        if (!mode.active() || dictionary.size() == 0) {
            return;
        }
        if (mode.enforcing() && coolingDown(uid)) {
            throw new ContentCooldownException(
                    "因近期多次触发内容安全策略，AI 功能暂时停用，约 " + properties.cooldownMinutes() + " 分钟后恢复。");
        }
        List<String> found = dictionary.find(text);
        if (found.isEmpty()) {
            return;
        }
        if (!mode.enforcing()) {
            record(uid, conversationUuid, ModerationDirection.INPUT, ModerationAction.LOGGED, found);
            return;
        }
        record(uid, conversationUuid, ModerationDirection.INPUT, ModerationAction.BLOCKED, found);
        strike(uid);
        throw new ContentBlockedException("提问包含不适宜内容，已被拦截。请修改措辞后重试。");
    }

    /**
     * Cleans a finished answer: masks credentials / contact identifiers, refuses an echoed system
     * prompt, then applies the dictionary in enforce mode.
     *
     * <p>Redaction runs in every mode, including {@link ModerationMode#OFF}: it is data hygiene, not
     * a content-policy decision. Leak detection and the dictionary follow the posture, so a shadow
     * deployment records without rewriting.</p>
     *
     * @param uid owner uid
     * @param conversationUuid conversation the answer belongs to
     * @param text the complete answer (streamed answers are accumulated server-side)
     * @return the text to persist and return, plus whether the safety layer altered it
     */
    public Screened screenAnswer(long uid, String conversationUuid, String text) {
        if (text == null || text.isEmpty()) {
            return new Screened(text, false);
        }
        OutputRedactor.Result redacted = OutputRedactor.redact(text);
        String answer = redacted.text();
        ModerationMode mode = mode();
        if (!mode.active()) {
            return new Screened(answer, redacted.changed());
        }
        if (redacted.changed()) {
            record(uid, conversationUuid, ModerationDirection.OUTPUT, ModerationAction.MASKED, redacted.kinds());
        }
        if (PromptLeakGuard.leaked(answer)) {
            if (!mode.enforcing()) {
                record(uid, conversationUuid, ModerationDirection.OUTPUT, ModerationAction.LOGGED,
                        List.of(PromptLeakGuard.HIT));
                return new Screened(answer, redacted.changed());
            }
            record(uid, conversationUuid, ModerationDirection.OUTPUT, ModerationAction.MASKED,
                    List.of(PromptLeakGuard.HIT));
            return new Screened(PromptLeakGuard.REFUSAL, true);
        }
        List<String> found = dictionary.size() == 0 ? List.of() : dictionary.find(answer);
        if (found.isEmpty()) {
            return new Screened(answer, redacted.changed());
        }
        if (!mode.enforcing()) {
            record(uid, conversationUuid, ModerationDirection.OUTPUT, ModerationAction.LOGGED, found);
            return new Screened(answer, redacted.changed());
        }
        record(uid, conversationUuid, ModerationDirection.OUTPUT, ModerationAction.MASKED, found);
        return new Screened(dictionary.mask(answer), true);
    }

    /**
     * @param uid owner uid
     * @param conversationUuid conversation that produced the memory
     * @param content normalized memory text
     * @return whether the memory must be refused
     */
    public boolean guardMemory(long uid, String conversationUuid, String content) {
        return recordStored(uid, conversationUuid, ModerationDirection.MEMORY, content);
    }

    /**
     * Rejects a profile preference that matches the dictionary.
     *
     * <p>Stored preferences are re-injected into every later turn, which makes them a permanent
     * injection point: without this check a user could park flagged text there and have it replayed
     * long after the question gate said no. Deliberately no strike — this is not an attempt to use
     * the assistant, and the update is rejected with a 400 the client can show as-is.</p>
     *
     * @param uid owner uid
     * @param text one preference line
     * @throws ContentBlockedException when the line matches in enforce mode
     */
    public void guardProfile(long uid, String text) {
        if (recordStored(uid, null, ModerationDirection.PROFILE, text)) {
            throw new ContentBlockedException("个人偏好包含不适宜内容，请修改后重试。");
        }
    }

    /**
     * Shared gate for text the user wants <em>stored</em> and re-injected later (memories, profile
     * preferences). Records the hit in both postures but only refuses in enforce mode.
     *
     * @return whether the text must be refused
     */
    private boolean recordStored(long uid, String conversationUuid, ModerationDirection direction,
            String text) {
        ModerationMode mode = mode();
        if (!mode.active() || text == null || text.isEmpty() || dictionary.size() == 0) {
            return false;
        }
        List<String> found = dictionary.find(text);
        if (found.isEmpty()) {
            return false;
        }
        ModerationAction action = mode.enforcing() ? ModerationAction.BLOCKED : ModerationAction.LOGGED;
        record(uid, conversationUuid, direction, action, found);
        return mode.enforcing();
    }

    /**
     * @param limit maximum rows to return
     * @return the newest hits first
     */
    public List<AiModerationHit> recent(int limit) {
        int size = Math.max(1, Math.min(MAX_REPORT_ROWS, limit));
        return hits.findAllByOrderByCreatedAtDesc(Limit.of(size));
    }

    /**
     * Review page over the audit rows.
     *
     * <p>Offset paging is acceptable here because the console shows an explicit page/total pair;
     * the keyset variant was dropped so every list endpoint exposes the same {@code Page} contract.
     * Newest rows come first.</p>
     *
     * @param direction optional direction filter
     * @param action optional action filter
     * @param keyword optional username / nickname / uid substring
     * @param from inclusive lower bound on {@code createdAt}, or {@code null}
     * @param to exclusive upper bound on {@code createdAt}, or {@code null}
     * @param page zero-based page index
     * @param size page size, clamped to the report maximum
     * @return one page of matching hits
     */
    @Transactional(readOnly = true)
    public Page<AiModerationHit> search(ModerationDirection direction, ModerationAction action,
            String keyword, Instant from, Instant to, int page, int size) {
        int safeSize = Math.max(1, Math.min(MAX_REPORT_ROWS, size));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return hits.findAll(AiModerationSpec.of(direction, action, keyword, from, to), pageable);
    }

    /**
     * Drops audit rows older than the retention window.
     *
     * @param before exclusive cutoff
     * @return how many rows were deleted
     */
    @Transactional
    public int purgeOlderThan(Instant before) {
        return (int) hits.deleteByCreatedAtBefore(before);
    }

    private void strike(long uid) {
        try {
            String key = STRIKE_PREFIX + uid;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(properties.strikeWindowMinutes()));
            }
            if (count != null && count >= properties.strikeLimit()) {
                redis.opsForValue().set(COOLDOWN_PREFIX + uid, "1",
                        Duration.ofMinutes(properties.cooldownMinutes()));
                LOG.info("AI moderation cooldown started for uid={} after {} strikes in {}m",
                        uid, count, properties.strikeWindowMinutes());
            }
        } catch (RuntimeException ex) {
            // Risk control must never break the request path; a missed strike only delays it.
            LOG.warn("Failed to record moderation strike for uid={}", uid, ex);
        }
    }

    private boolean coolingDown(long uid) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(COOLDOWN_PREFIX + uid));
        } catch (RuntimeException ex) {
            LOG.warn("Failed to read moderation cooldown for uid={}", uid, ex);
            return false;
        }
    }

    private void record(long uid, String conversationUuid, ModerationDirection direction,
            ModerationAction action, List<String> found) {
        String joined = String.join(",", found);
        String words = joined.length() > 255 ? joined.substring(0, 255) : joined;
        String owner = StringUtil.isBlank(conversationUuid) ? null : conversationUuid;
        try {
            audit.executeWithoutResult(status -> {
                AiModerationHit hit = new AiModerationHit();
                hit.setUid(uid);
                hit.setConversationUuid(owner);
                hit.setDirection(direction);
                hit.setAction(action);
                hit.setWords(words);
                hit.setHitCount(found.size());
                hit.setCreatedAt(Instant.now());
                hits.save(hit);
            });
        } catch (RuntimeException ex) {
            LOG.warn("Failed to record moderation hit for uid={} direction={}", uid, direction, ex);
        }
    }

    /**
     * Result of the answer screen.
     *
     * @param text the text to return and persist (altered when {@code masked})
     * @param masked whether the safety layer altered the text: credential/PII redaction, a refused
     *     system-prompt leak, or dictionary masking; drives the stored {@code moderated} flag
     */
    public record Screened(String text, boolean masked) {
    }
}
