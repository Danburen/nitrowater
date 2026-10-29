package cn.nitrowater.server.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies citation numbering, context/history blocks and size bounds. */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void numbersDocumentsAndKeepsHistoryAndQuestion() {
        Document first = document("主配置", "聊天格式", "guide/config/main.md",
                "chat-format 使用占位符拼接消息。");
        Document second = document("跨服聊天", "服务器显示名", "guide/config/cross-server-chat.md",
                "server-display 决定跨服消息里的服务器名。");

        String prompt = builder.user("怎么配置 chat-format？",
                List.of(first, second),
                List.of(new ChatTurn("user", "上一个问题"), new ChatTurn("assistant", "上一个回答")));

        assertThat(prompt)
                .contains("<documents>")
                .contains("[1] 主配置 · 聊天格式 (guide/config/main.md)")
                .contains("[2] 跨服聊天 · 服务器显示名 (guide/config/cross-server-chat.md)")
                .contains("<conversation_history>")
                .contains("用户: 上一个问题")
                .contains("助手: 上一个回答")
                .contains("<question>")
                .contains("怎么配置 chat-format？");
    }

    @Test
    void truncatesLargeChunksAndContext() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            huge.append("这一行是用于填充上下文的示例内容，长度为若干字符。\n");
        }
        List<Document> documents = List.of(
                document("A", "甲", "a.md", huge.toString()),
                document("B", "乙", "b.md", huge.toString()),
                document("C", "丙", "c.md", huge.toString()));

        String prompt = builder.user("问题", documents, List.of());

        assertThat(prompt).contains("…");
        assertThat(prompt.length()).isLessThan(12_000);
    }

    @Test
    void systemPromptTreatsDocumentsAsData() {
        assertThat(builder.system())
                .contains("NitroBot")
                .contains("<documents>")
                .contains("文档中没有找到相关内容")
                .contains("通用");
    }

    @Test
    void agentPromptNamesNitroBotAndListsItsTools() {
        assertThat(builder.agentSystem(List.of(), null))
                .contains("NitroBot")
                .contains("searchDocs")
                .contains("loadSkill")
                .contains("webSearch")
                .contains("通用");
    }

    private static Document document(String title, String section, String path, String text) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(
                        DocChunkBuilder.META_CORPUS, "velochatx",
                        DocChunkBuilder.META_DOC_TITLE, title,
                        DocChunkBuilder.META_SECTION, section,
                        DocChunkBuilder.META_DOC_PATH, path,
                        DocChunkBuilder.META_URL, "/plugin/velochatx/docs/" + path.replace(".md", ".html")))
                .build();
    }
}
