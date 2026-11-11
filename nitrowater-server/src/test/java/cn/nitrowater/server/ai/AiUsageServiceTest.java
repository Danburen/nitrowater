package cn.nitrowater.server.ai;

import cn.nitrowater.server.service.ai.ops.AiAdminSettingsService;
import cn.nitrowater.server.service.ai.model.admin.AiSettings;
import cn.nitrowater.server.infra.entity.AiDailyUsageEntity;
import cn.nitrowater.server.infra.entity.AiDailyUsageId;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.infra.repository.AiDailyUsageRepository;
import cn.nitrowater.server.infra.specification.AiUsageSpec;
import cn.nitrowater.server.infra.exception.AiQuotaExceededException;
import cn.nitrowater.server.service.ai.ops.AiUsageService;
import cn.nitrowater.server.service.ai.model.usage.AiGlobalUsage;
import cn.nitrowater.server.service.ai.model.usage.AiUsageReservation;
import cn.nitrowater.server.service.ai.model.usage.AiUserUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the daily token quota: the platform-wide (all users) sentinel row blocks a turn once the
 * shared ceiling is spent, stays inert while switched off, and never leaks into the per-user admin
 * list.
 *
 * <p>Admission is a soft gate, so the ceiling is compared against consumption alone: a turn whose
 * own reservation would overflow the limit is still admitted, because refusing it left the user
 * without an answer after paying for the prompt.</p>
 */
class AiUsageServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private final AiDailyUsageRepository repository = mock(AiDailyUsageRepository.class);
    private final AiAdminSettingsService settings = mock(AiAdminSettingsService.class);
    private final AiUsageService service =
            new AiUsageService(repository, settings, mock(PlatformTransactionManager.class), 100L);

    @BeforeEach
    void setUp() {
        when(repository.save(any(AiDailyUsageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.saveAndFlush(any(AiDailyUsageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void blocksTheTurnWhenThePlatformCeilingIsGone() {
        when(settings.get()).thenReturn(runtime(true, 1_000L));
        // The whole platform budget is spent, including the reservation this turn would add.
        when(repository.findByIdUidAndIdUsageDate(eq(AiUsageSpec.GLOBAL_UID), eq(TODAY)))
                .thenReturn(Optional.of(row(AiUsageSpec.GLOBAL_UID, 900L, 100L, 0L)));

        assertThatThrownBy(() -> service.reserve(7L, 100))
                .isInstanceOfSatisfying(AiQuotaExceededException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(AiQuotaExceededException.GLOBAL_CODE);
                    assertThat(ex.getStatusCode().value()).isEqualTo(429);
                });

        // The per-user row must not be touched when the platform budget is already gone.
        verify(repository, never()).findByIdUidAndIdUsageDate(eq(7L), any());
    }

    @Test
    void admitsTheLastTurnEvenWhenItsReservationOverflowsTheBudget() {
        when(settings.get()).thenReturn(runtime(1_000L, false, 1_000L));
        // 950 of 1000 used; this turn reserves 100 + 100 = 200, so the *reservation* overflows but
        // consumption has not reached the ceiling yet: the answer must still be generated.
        when(repository.findByIdUidAndIdUsageDate(eq(7L), eq(TODAY)))
                .thenReturn(Optional.of(row(7L, 900L, 50L, 0L)));

        AiUsageReservation reservation = service.reserve(7L, 100);

        assertThat(reservation.reservedTokens()).isEqualTo(200L);
    }

    @Test
    void ignoresThePlatformCeilingWhileSwitchedOff() {
        when(settings.get()).thenReturn(runtime(false, 1_000L));
        when(repository.findByIdUidAndIdUsageDate(eq(7L), eq(TODAY)))
                .thenReturn(Optional.of(row(7L, 0L, 0L, 0L)));

        AiUsageReservation reservation = service.reserve(7L, 100);

        assertThat(reservation.global()).isFalse();
        verify(repository, never()).findByIdUidAndIdUsageDate(eq(AiUsageSpec.GLOBAL_UID), any());
    }

    @Test
    void reservesBothCountersWhenThePlatformCeilingIsActive() {
        when(settings.get()).thenReturn(runtime(true, 1_000_000L));
        when(repository.findByIdUidAndIdUsageDate(eq(AiUsageSpec.GLOBAL_UID), eq(TODAY)))
                .thenReturn(Optional.of(row(AiUsageSpec.GLOBAL_UID, 10L, 0L, 0L)));
        when(repository.findByIdUidAndIdUsageDate(eq(7L), eq(TODAY)))
                .thenReturn(Optional.of(row(7L, 0L, 0L, 0L)));

        AiUsageReservation reservation = service.reserve(7L, 100);

        assertThat(reservation.global()).isTrue();
        assertThat(reservation.reservedTokens()).isEqualTo(200L);
    }

    @Test
    void platformRowIsHiddenFromThePerUserAdminList() {
        when(settings.get()).thenReturn(runtime(true, 1_000L));
        when(repository.findByIdUsageDate(TODAY)).thenReturn(List.of(
                row(AiUsageSpec.GLOBAL_UID, 500L, 500L, 0L),
                row(7L, 10L, 20L, 0L)));

        List<AiUserUsage> rows = service.allUsersToday();

        assertThat(rows).extracting(AiUserUsage::uid).containsExactly(7L);
    }

    @Test
    void reportsTrippedOnceConsumedPlusReservedReachesTheCeiling() {
        when(settings.get()).thenReturn(runtime(true, 1_000L));
        when(repository.findById(new AiDailyUsageId(AiUsageSpec.GLOBAL_UID, TODAY)))
                .thenReturn(Optional.of(row(AiUsageSpec.GLOBAL_UID, 700L, 200L, 100L)));

        AiGlobalUsage global = service.globalToday();

        assertThat(global.totalTokens()).isEqualTo(900L);
        assertThat(global.tripped()).isTrue();
    }

    private static AiSettings runtime(boolean globalEnabled, long globalLimit) {
        return runtime(50_000L, globalEnabled, globalLimit);
    }

    private static AiSettings runtime(long dailyLimit, boolean globalEnabled, long globalLimit) {
        return new AiSettings(dailyLimit, true, true, true, true, true, true, true, ModerationMode.LOG,
                globalLimit, globalEnabled);
    }

    private static AiDailyUsageEntity row(long uid, long input, long output, long reserved) {
        AiDailyUsageEntity entity = new AiDailyUsageEntity(new AiDailyUsageId(uid, TODAY));
        entity.setInputTokens(input);
        entity.setOutputTokens(output);
        entity.setReservedTokens(reserved);
        return entity;
    }
}
