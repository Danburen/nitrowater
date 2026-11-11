package cn.nitrowater.core.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for the SSE error shape.
 *
 * <p>An SSE client sends {@code Accept: text/event-stream} and nothing else. A rejection raised
 * before the stream opens (unknown conversation, disabled mode, quota) used to be written through
 * content negotiation, which failed with {@code HttpMediaTypeNotAcceptableException}: the browser
 * received a bare 404 with no body and no message. Pinning {@code Content-Type: application/json}
 * on the reply makes the converter skip negotiation, so the envelope always reaches the client.</p>
 */
class SseErrorEnvelopeTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void sseClientReceivesTheJsonEnvelope() throws Exception {
        mvc.perform(get("/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("http_404"))
                .andExpect(jsonPath("$.message").value("Conversation not found"));
    }

    @Test
    void jsonClientKeepsItsEnvelope() throws Exception {
        mvc.perform(get("/plain").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("http_404"));
    }

    /** Mirrors the real endpoints: one SSE-only route, one ordinary JSON route, same rejection. */
    @RestController
    static class ProbeController {

        @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String stream() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        @GetMapping("/plain")
        String plain() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
    }
}
