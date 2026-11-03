package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.service.ai.model.DocCollection;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies markdown discovery, front-matter handling, metadata and deterministic chunk ids. */
class DocChunkBuilderTest {

    @TempDir
    Path docs;

    private DocChunkBuilder builder() {
        return new DocChunkBuilder(properties(docs));
    }

    @Test
    void buildsChunksWithStableMetadata() throws IOException {
        write("index.md", """
                ---
                layout: home
                title: Home
                ---

                # VeloChatX

                一款运行在 Velocity 上的跨服聊天插件。
                """);
        write("guide/config/main.md", """
                # 主配置

                配置文件位于 `config.yml`。

                ## 聊天格式

                ```yaml
                chat-format: "{Player}: {Message}"
                ```

                ## 目录结构

                ```
                .
                ├── broadcast.yml
                └── config.yml
                ```
                """);
        write("en/guide/config/main.md", """
                # Main configuration

                The configuration lives in `config.yml` and supports many placeholders.
                """);

        DocChunkBuilder.Result result = builder().build();

        assertThat(result.files()).isEqualTo(3);
        assertThat(result.chunks()).isNotEmpty();

        List<Document> chunks = result.chunks();
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getMetadata())
                        .containsEntry(DocChunkBuilder.META_CORPUS, "velochatx")
                        .containsKey(DocChunkBuilder.META_DOC_PATH)
                        .containsKey(DocChunkBuilder.META_URL));
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getText()).doesNotContain("layout: home"));

        Document zh = chunk(chunks, "guide/config/main.md", "zh");
        assertThat(zh.getMetadata())
                .containsEntry(DocChunkBuilder.META_DOC_TITLE, "主配置")
                .containsEntry(DocChunkBuilder.META_URL, "/plugin/velochatx/docs/guide/config/main.html");

        Document en = chunk(chunks, "en/guide/config/main.md", "en");
        assertThat(en.getMetadata())
                .containsEntry(DocChunkBuilder.META_URL, "/plugin/velochatx/docs/en/guide/config/main.html");

        Document home = chunk(chunks, "index.md", "zh");
        assertThat(home.getMetadata())
                .containsEntry(DocChunkBuilder.META_URL, "/plugin/velochatx/docs/");
    }

    @Test
    void chunkIdsAreDeterministicAcrossBuilds() throws IOException {
        write("guide/intro/get-start.md", """
                # 快速开始

                下载 jar 并放入服务器的 plugins 目录，然后重启代理端即可。
                """);

        List<String> first = builder().build().chunks().stream().map(Document::getId).toList();
        List<String> second = builder().build().chunks().stream().map(Document::getId).toList();

        assertThat(first).isNotEmpty().isEqualTo(second);
    }

    @Test
    void missingDirectoryYieldsEmptyResult() throws IOException {
        DocChunkBuilder.Result result = new DocChunkBuilder(properties(docs.resolve("missing"))).build();

        assertThat(result.files()).isZero();
        assertThat(result.chunks()).isEmpty();
    }

    @Test
    void acceptsFileUriPrefixedLocation() throws IOException {
        write("index.md", """
                # Prefixed corpus

                Deployed corpora are usually written as file:/srv/docs/<corpus>.
                """);

        AiProperties properties = new AiProperties(true,
                500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                "deploy/ai-skills",
                List.of(new DocCollection("nitrowater", "file:" + docs, "")));

        DocChunkBuilder.Result result = new DocChunkBuilder(properties).build();

        assertThat(result.files()).isEqualTo(1);
        assertThat(result.chunks()).isNotEmpty();
    }

    @Test
    void buildsChunksForFilesystemAndClasspathCollections() throws IOException {
        write("index.md", """
                # Filesystem corpus

                This document belongs to the temporary collection.
                """);

        AiProperties properties = new AiProperties(true,
                500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                "deploy/ai-skills", List.of(
                        new DocCollection("velochatx", docs.toString(), "/docs/velochatx/"),
                        new DocCollection("nitrowater", "classpath:ai/docs/nitrowater", "/docs/nitrowater/")));

        DocChunkBuilder.Result result = new DocChunkBuilder(properties).build();

        assertThat(result.files()).isGreaterThan(1);
        assertThat(result.chunks()).anySatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry(DocChunkBuilder.META_CORPUS, "velochatx")
                .containsEntry(DocChunkBuilder.META_URL, "/docs/velochatx/"));
        assertThat(result.chunks()).anySatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry(DocChunkBuilder.META_CORPUS, "nitrowater"));
        assertThat(result.chunks())
                .filteredOn(chunk -> "nitrowater".equals(chunk.getMetadata().get(DocChunkBuilder.META_CORPUS)))
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get(DocChunkBuilder.META_URL))
                        .asString().startsWith("/docs/nitrowater/"));
    }

    @Test
    void chunkIdsIncludeTheCorpus() {
        DocChunkBuilder builder = builder();

        assertThat(builder.chunkId("velochatx", "index.md", 0))
                .isNotEqualTo(builder.chunkId("nitrowater", "index.md", 0));
    }

    @Test
    void missingFileNameFallsBackToUntitled() {
        assertThat(DocChunkBuilder.extractTitle("no heading", null)).isEqualTo("untitled");
    }

    @Test
    void mapsMarkdownPathsToPublishedUrls() {
        assertThat(DocChunkBuilder.toPublicUrl("index.md", "/plugin/velochatx/docs/"))
                .isEqualTo("/plugin/velochatx/docs/");
        assertThat(DocChunkBuilder.toPublicUrl("en/index.md", "/plugin/velochatx/docs/"))
                .isEqualTo("/plugin/velochatx/docs/en/");
        assertThat(DocChunkBuilder.toPublicUrl("guide/intro/get-start.md", "/plugin/velochatx/docs"))
                .isEqualTo("/plugin/velochatx/docs/guide/intro/get-start.html");
    }

    private Document chunk(List<Document> chunks, String path, String language) {
        Map<String, Object> metadata = Map.of(
                DocChunkBuilder.META_DOC_PATH, path,
                DocChunkBuilder.META_LANG, language);
        return chunks.stream()
                .filter(chunk -> metadata.entrySet().stream()
                        .allMatch(entry -> entry.getValue().equals(chunk.getMetadata().get(entry.getKey()))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no chunk for " + path));
    }

    private void write(String relative, String content) throws IOException {
        Path file = docs.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static AiProperties properties(Path docsDir) {
        return new AiProperties(true,
                500, 100, 40, 6, 0.35, 2000, 4, false, 20,
                "deploy/ai-skills", List.of(new DocCollection("velochatx", docsDir.toString(), "/plugin/velochatx/docs/")));
    }
}
