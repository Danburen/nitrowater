package cn.nitrowater.server.web;

import cn.nitrowater.server.infra.exception.AiQuotaExceededException;
import cn.nitrowater.server.infra.exception.AiTurnTimeoutException;
import cn.nitrowater.server.web.exception.AiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import cn.nitrowater.lib.api.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the machine-readable body the web client relies on to switch to its exhausted state. */
class AiExceptionHandlerTest {

    @Test
    void mapsQuotaExhaustionToATokenQuotaBody() {
        ResponseEntity<ApiResponse<Void>> response =
                new AiExceptionHandler().handle(new AiQuotaExceededException("今日 AI token 额度已用完，请明天再试"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("token_quota");
        assertThat(response.getBody().getMessage()).isEqualTo("今日 AI token 额度已用完，请明天再试");
    }

    @Test
    void mapsATurnTimeoutToAGatewayTimeoutBody() {
        ResponseEntity<ApiResponse<Void>> response =
                new AiExceptionHandler().handle(new AiTurnTimeoutException(150_000L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("turn_timeout");
        assertThat(response.getBody().getMessage()).contains("150 秒");
    }
}
