package cn.nitrowater.core.application.auth.code;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies delivery-before-storage and one-time verification semantics. */
class AbstractRedisCodeSenderTest {

    private final RedisHelperHolder redis = mock(RedisHelperHolder.class);

    @Test
    void sendsAndStoresTheCodeAfterDelivery() {
        TestSender sender = new TestSender(redis, 5);

        CodeResult result = sender.sendCode("user@example.com", VerifyScene.REGISTER);

        assertThat(result.isSendSuccess()).isTrue();
        assertThat(result.getKey()).isNotBlank();
        assertThat(sender.deliveredCode).hasSize(6);
        verify(redis).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void deliveryFailureDoesNotStoreAVerifiableCode() {
        TestSender sender = new TestSender(redis, 5);
        sender.failDelivery = true;

        assertThatThrownBy(() -> sender.sendCode("user@example.com", VerifyScene.REGISTER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider failed");

        verify(redis, never()).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void verifyUsesTheChannelSpecificRedisKey() {
        when(redis.validateAndRemove(anyString(), eq("123456"))).thenReturn(true);
        TestSender sender = new TestSender(redis, 5);

        assertThat(sender.verifyCode("user@example.com", VerifyScene.REGISTER, "key-1", "123456"))
                .isTrue();
        verify(redis).validateAndRemove(anyString(), eq("123456"));
    }

    private static final class TestSender extends AbstractRedisCodeSender {

        private String deliveredCode;
        private boolean failDelivery;

        private TestSender(RedisHelperHolder redis, long expireMinutes) {
            super(redis, VerifyChannel.EMAIL, expireMinutes);
        }

        @Override
        protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
            if (failDelivery) {
                throw new IllegalStateException("provider failed");
            }
            deliveredCode = code;
        }
    }
}
