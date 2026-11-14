package cn.nitrowater.core.application.auth.code;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.SendCodeReq;
import cn.nitrowater.core.exception.InvalidVerifySceneException;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.core.infrastructure.persistence.user.UserDatumRepo;
import cn.nitrowater.core.infrastructure.security.EncryptedKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Guards the anonymous send whitelist: registration proves the phone only, so an email code for
 * that scene must never leave the server (the user center owns email binding).
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
    void registerSceneRejectsEmailChannel() {
        SendCodeReq req = new SendCodeReq();
        req.setTarget("someone@example.com");
        req.setChannel(VerifyChannel.EMAIL);
        req.setScene(VerifyScene.REGISTER);

        assertThrows(InvalidVerifySceneException.class, () -> service.sendCodeForAnonymous(req));
        verifyNoInteractions(codeSenderFactory);
    }
}
