package cn.nitrowater.server.ai.model.tool;

import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import cn.nitrowater.server.service.ai.model.tool.ToolResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the single size ceiling every tool result passes through. */
class ToolResponseTest {

    @Test
    void keepsAPayloadUnderTheCeilingVerbatim() {
        ToolResponse response = ToolResponse.of(ToolCode.OK, "done", Map.of("stdout", "hello"));

        String json = response.json();

        assertThat(json).contains("\"success\":true").contains("hello");
        assertThat(json).doesNotContain("truncated");
    }

    /**
     * One round of tool output is replayed by every later round, so an unbounded payload (the
     * sandbox alone allows 64KB per stream) would eat the whole context budget. The envelope has to
     * stay parseable, otherwise the model cannot read the code and the message.
     */
    @Test
    void truncatesAnOversizedPayloadButKeepsTheEnvelopeValid() {
        ToolResponse response = ToolResponse.of(ToolCode.OK, "compiled and ran",
                Map.of("stdout", "x".repeat(60_000)));

        String json = response.json();

        assertThat(json.length()).isLessThan(17_000);
        assertThat(json)
                .contains("\"success\":true")
                .contains("compiled and ran")
                .contains("\"truncated\":true")
                .contains("\"originalChars\"");
    }

    /**
     * The ceiling applies to the serialized result, not to one field: many medium fields must be
     * capped just like one huge field.
     */
    @Test
    void capsTheWholeSerializedResultNotOneField() {
        Map<String, Object> wide = new LinkedHashMap<>();
        for (int index = 0; index < 40; index++) {
            wide.put("result" + index, "y".repeat(500));
        }

        String json = ToolResponse.of(ToolCode.OK, "many results", wide).json();

        assertThat(json.length()).isLessThan(17_000);
        assertThat(json).contains("\"truncated\":true");
    }
}
