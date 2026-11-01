package cn.nitrowater.core.application.account.impl;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.application.auth.SingleUseTokenService;
import cn.nitrowater.core.application.auth.code.VerificationService;
import cn.nitrowater.core.application.user.UserCoreService;
import cn.nitrowater.core.application.user.UserDatumCoreService;
import cn.nitrowater.core.domain.user.UserDatum;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.persistence.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies contact changes stay pending in Redis until the new target proves ownership. */
@ExtendWith(MockitoExtension.class)
class AccountCoreServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserDatumRepo userDatumRepo;
    @Mock private UserCoreService userCoreService;
    @Mock private UserDatumCoreService userDatumCoreService;
    @Mock private VerificationService verificationService;
    @Mock private RedisHelperHolder redisHelperHolder;
    @Mock private SingleUseTokenService singleUseTokenService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private AccountCoreServiceImpl service;

    @Test
    void changingEmailStoresThePendingTargetUntilVerified() {
        String email = "new@example.com";
        when(verificationService.sendCodeForAuthenticated(
                email, VerifyChannel.EMAIL, VerifyScene.ACTIVATE))
                .thenReturn(CodeResult.success(email, VerifyChannel.EMAIL).withKey("verify-1"));

        service.changeEmail(7L, " New@Example.com ", null);

        verify(userDatumCoreService).validateEmailAvailable(7L, email);
        verify(redisHelperHolder).set(
                "op:verify:context:contact:email:7:verify-1",
                "change|" + email, Duration.ofMinutes(30));
        verify(userDatumCoreService, never()).saveNewEmail(anyLong(), any(), eq(false));
    }

    @Test
    void verifyingEmailAppliesThePendingTargetAndDeletesTheContext() {
        String key = "op:verify:context:contact:email:7:verify-1";
        when(redisHelperHolder.getValue(key)).thenReturn("change|new@example.com");

        service.verifyChangeEmail("verify-1", "123456", 7L, null);

        verify(verificationService).verifyCode(
                "new@example.com", VerifyScene.ACTIVATE, VerifyChannel.EMAIL, "verify-1", "123456");
        verify(userDatumCoreService).saveNewEmail(7L, "new@example.com", true);
        verify(redisHelperHolder).del(key);
    }

    @Test
    void activatingAnExistingEmailMarksItVerifiedWithoutRebinding() {
        UserDatum datum = new UserDatum();
        datum.setEmailEncrypted("ciphertext");
        datum.setEmailVerified(false);
        when(userDatumCoreService.getRawEmail(7L)).thenReturn("old@example.com");
        when(userDatumCoreService.getUserDatum(7L)).thenReturn(datum);
        when(verificationService.sendCodeForAuthenticated(
                "old@example.com", VerifyChannel.EMAIL, VerifyScene.ACTIVATE))
                .thenReturn(CodeResult.success("old@example.com", VerifyChannel.EMAIL).withKey("verify-2"));
        String key = "op:verify:context:contact:email:7:verify-2";
        when(redisHelperHolder.getValue(key)).thenReturn("activate|old@example.com");

        service.activateEmail(7L, null, null);
        service.verifyChangeEmail("verify-2", "123456", 7L, null);

        verify(userDatumCoreService).markEmailVerified(7L);
        verify(userDatumCoreService, never()).saveNewEmail(7L, "old@example.com", true);
    }
}
