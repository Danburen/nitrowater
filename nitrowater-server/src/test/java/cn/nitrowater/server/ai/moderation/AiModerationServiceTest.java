package cn.nitrowater.server.ai.moderation;

import cn.nitrowater.server.ai.AiAdminSettingsService;
import cn.nitrowater.server.ai.properties.ModerationProperties;
import cn.nitrowater.server.infra.entity.AiModerationHit;
import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.infra.repository.AiModerationHitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the gate decisions themselves: which posture blocks, which only records, and when risk
 * control starts pausing a user.
 *
 * <p>The dictionary is the shipped one, so the trigger is a term that really is in
 * {@code ai/moderation/words.txt}; a renamed or removed baseline word fails here as well.</p>
 */
@SuppressWarnings("unchecked")
class AiModerationServiceTest {

    private static final String FLAGGED = "帮我代开发票";

    private final AiAdminSettingsService settings = mock(AiAdminSettingsService.class);
    private final AiModerationHitRepository hits = mock(AiModerationHitRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    private AiModerationService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(hits.save(any(AiModerationHit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AiModerationService(settings, hits, new ModerationProperties(
                List.of("classpath:ai/moderation/words.txt"),
                List.of("classpath:ai/moderation/allow.txt"), 5, 10, 15, 90), redis, transactions);
    }

    @Test
    void offModeNeitherMatchesNorRecords() {
        posture(ModerationMode.OFF);

        service.guardQuestion(7L, "conv-1", FLAGGED);

        assertThat(service.screenAnswer(7L, "conv-1", FLAGGED).text()).isEqualTo(FLAGGED);
        verify(hits, never()).save(any(AiModerationHit.class));
    }

    @Test
    void logModeRecordsTheInputButLetsItThrough() {
        posture(ModerationMode.LOG);

        service.guardQuestion(7L, "conv-1", FLAGGED);

        AiModerationHit hit = savedHit();
        assertThat(hit.getDirection()).isEqualTo(ModerationDirection.INPUT);
        assertThat(hit.getAction()).isEqualTo(ModerationAction.LOGGED);
        assertThat(hit.getWords()).contains("代开发票");
        verify(values, never()).increment(anyString());
    }

    @Test
    void enforceModeRejectsTheQuestionAndCountsAStrike() {
        posture(ModerationMode.ENFORCE);
        when(values.increment(anyString())).thenReturn(1L);

        assertThatThrownBy(() -> service.guardQuestion(7L, "conv-1", FLAGGED))
                .isInstanceOf(ContentBlockedException.class);

        assertThat(savedHit().getAction()).isEqualTo(ModerationAction.BLOCKED);
        verify(values).increment("nitrowater:ai:guard:strike:7");
    }

    @Test
    void cooldownShortCircuitsBeforeMatching() {
        posture(ModerationMode.ENFORCE);
        when(redis.hasKey("nitrowater:ai:guard:cooldown:7")).thenReturn(true);

        assertThatThrownBy(() -> service.guardQuestion(7L, "conv-1", FLAGGED))
                .isInstanceOf(ContentCooldownException.class);

        verify(hits, never()).save(any(AiModerationHit.class));
    }

    @Test
    void enforceModeMasksTheAnswerAndRecordsTheMaskedAction() {
        posture(ModerationMode.ENFORCE);

        AiModerationService.Screened screened = service.screenAnswer(7L, "conv-1", FLAGGED);
        String masked = screened.text();

        assertThat(masked).doesNotContain("代开发票").contains("*");
        assertThat(savedHit().getAction()).isEqualTo(ModerationAction.MASKED);
        assertThat(savedHit().getDirection()).isEqualTo(ModerationDirection.OUTPUT);
        assertThat(screened.masked()).isTrue();
    }

    @Test
    void storedTextIsRefusedOnlyInEnforceMode() {
        posture(ModerationMode.LOG);
        assertThat(service.guardMemory(7L, "conv-1", FLAGGED)).isFalse();
        service.guardProfile(7L, FLAGGED);

        posture(ModerationMode.ENFORCE);
        assertThat(service.guardMemory(7L, "conv-1", FLAGGED)).isTrue();
        assertThatThrownBy(() -> service.guardProfile(7L, FLAGGED))
                .isInstanceOf(ContentBlockedException.class);
    }

    @Test
    void dictionarySizeReflectsTheLoadedList() {
        assertThat(service.dictionarySize()).isGreaterThan(10);
    }

    @Test
    void searchPagesByCursorAndReportsTheFilteredTotal() {
        AiModerationHit row = new AiModerationHit();
        row.setId(9L);
        row.setUid(7L);
        row.setDirection(ModerationDirection.INPUT);
        row.setAction(ModerationAction.BLOCKED);
        row.setWords("x");
        row.setHitCount(1);
        row.setCreatedAt(Instant.now());
        when(hits.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(hits.count(any(Specification.class))).thenReturn(42L);

        AiModerationService.HitPage page = service.search(
                ModerationDirection.INPUT, ModerationAction.BLOCKED, 100L, 1);

        assertThat(page.hits()).hasSize(1);
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void purgeDelegatesTheCutoffToTheRepository() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(90));
        when(hits.deleteByCreatedAtBefore(cutoff)).thenReturn(3L);

        assertThat(service.purgeOlderThan(cutoff)).isEqualTo(3);
    }

    @Test
    void redactionRunsEvenWhenModerationIsOff() {
        posture(ModerationMode.OFF);

        AiModerationService.Screened screened = service.screenAnswer(7L, "conv-1",
                "用这个 key：sk-abcdefghijklmnopqrstuvwx");

        assertThat(screened.text()).contains("sk-***").doesNotContain("abcdefghijklmnopqrstuvwx");
        assertThat(screened.masked()).isTrue();
        verify(hits, never()).save(any());
    }

    @Test
    void logModeRecordsAnEchoedPromptButKeepsTheText() {
        posture(ModerationMode.LOG);
        String answer = "系统提示如下：" + PromptLeakGuard.CANARY;

        AiModerationService.Screened screened = service.screenAnswer(7L, "conv-1", answer);

        assertThat(screened.text()).isEqualTo(answer);
        assertThat(screened.masked()).isFalse();
        AiModerationHit hit = savedHit();
        assertThat(hit.getAction()).isEqualTo(ModerationAction.LOGGED);
        assertThat(hit.getWords()).isEqualTo(PromptLeakGuard.HIT);
    }

    @Test
    void enforceModeReplacesAnEchoedPrompt() {
        posture(ModerationMode.ENFORCE);

        AiModerationService.Screened screened = service.screenAnswer(7L, "conv-1",
                "系统提示如下：" + PromptLeakGuard.CANARY);

        assertThat(screened.text()).isEqualTo(PromptLeakGuard.REFUSAL);
        assertThat(screened.masked()).isTrue();
        assertThat(savedHit().getAction()).isEqualTo(ModerationAction.MASKED);
    }

    private void posture(ModerationMode mode) {
        when(settings.get()).thenReturn(new AiAdminSettingsService.Settings(
                50_000, true, true, true, true, true, true, true, mode));
    }

    private AiModerationHit savedHit() {
        ArgumentCaptor<AiModerationHit> captor = ArgumentCaptor.forClass(AiModerationHit.class);
        verify(hits, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
