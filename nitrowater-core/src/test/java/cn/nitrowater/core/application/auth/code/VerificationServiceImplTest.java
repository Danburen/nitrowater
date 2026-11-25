package cn.nitrowater.core.application.auth.code;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.SendCodeReq;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.infrastructure.domain.EncryptionDataKey;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;
import cn.nitrowater.lib.api.BaseResponseCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the anonymous send whitelist: registration may prove either a phone or an email, and a
 * login code is never spent on a target that has no account.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

    @Mock private CodeSenderFactory codeSenderFactory;
    @Mock private CodeVerifierFactory codeVerifierFactory;
    @Mock private EncryptedKeyService encryptedKeyService;
    @Mock private UserDatumRepo userDatumRepo;
    @Mock private RedisHelperHolder redisHelper;
    @InjectMocks private VerificationServiceImpl service;

    @Test
    void registerSceneSendsEmailCodeToFreeAddress() {
        EncryptionDataKey hmacKey = mock(EncryptionDataKey.class);
        when(hmacKey.getEncryptedKey()).thenReturn("hmac-key");
        when(encryptedKeyService.getUserDatumHmacKey()).thenReturn(hmacKey);
        when(userDatumRepo.findByEmailHash(anyString())).thenReturn(Optional.empty());
        CodeSender sender = mock(CodeSender.class);
        when(codeSenderFactory.of(VerifyChannel.EMAIL)).thenReturn(sender);
        when(sender.sendCode("someone@example.com", VerifyScene.REGISTER)).thenReturn(
                CodeResult.builder()
                        .sendSuccess(true)
                        .target("someone@example.com")
                        .channel(VerifyChannel.EMAIL)
                        .key("email-code-key")
                        .build());

        SendCodeReq req = new SendCodeReq();
        req.setTarget("someone@example.com");
        req.setChannel(VerifyChannel.EMAIL);
        req.setScene(VerifyScene.REGISTER);

        CodeResult result = service.sendCodeForAnonymous(req);
        assertEquals("email-code-key", result.getKey());
        verify(sender).sendCode("someone@example.com", VerifyScene.REGISTER);
    }

    @Test
    void loginSceneReportsAnUnregisteredPhoneInsteadOfSendingNothing() {
        EncryptionDataKey hmacKey = mock(EncryptionDataKey.class);
        when(hmacKey.getEncryptedKey()).thenReturn("hmac-key");
        when(encryptedKeyService.getUserDatumHmacKey()).thenReturn(hmacKey);
        when(userDatumRepo.findByPhoneHash(anyString())).thenReturn(Optional.empty());

        SendCodeReq req = new SendCodeReq();
        req.setTarget("13800138000");
        req.setChannel(VerifyChannel.SMS);
        req.setScene(VerifyScene.LOGIN);

        BizException error = assertThrows(BizException.class, () -> service.sendCodeForAnonymous(req));
        assertEquals(BaseResponseCode.VERIFY_TARGET_NOT_REGISTERED.getCode(), error.getErrorCode());
        verifyNoInteractions(codeSenderFactory);
    }
}
