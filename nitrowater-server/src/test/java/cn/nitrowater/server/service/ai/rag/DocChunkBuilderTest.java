package cn.nitrowater.server.service.ai.rag;

import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import cn.nitrowater.server.config.ai.AiProperties;
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

/**
 * Verifies corpus discovery, markdown discovery, front-matter handling, metadata and deterministic
 * chunk ids. The temp directory is the docs root; {@code velochatx/} inside it is one corpus.
 */
class DocChunkBuilderTest {

    @TempDir
    Path root;

    private DocChunkBuilder builder() {
        AiProperties properties = properties(root);
        return new DocChunkBuilder(properties, new DocCorpusRegistry(properties));
    }

    @Test
    void buildsChunksWithStableMetadata() throws IOException {
        writeCorpus("/plugin/velochatx/docs/");
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
        assertThat(result.docs()).allSatisfy(doc -> {
            assertThat(doc.corpus()).isEqualTo("velochatx");
            assertThat(doc.sha256()).hasSize(64);
            assertThat(doc.chunks()).isPositive();
        });

        List<Document> chunks = result.chunks();
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getMetadata())
                        .containsEntry(DocChunkBuilder.META_CORPUS, "velochatx")
                        .containsKey(DocChunkBuilder.META_DOC_PATH)
                        .containsKey(DocChunkBuilder.META_CONTENT_SHA)
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
    void discoversEverySubdirectoryAsACorpus() throws IOException {
        writeCorpus("");
        write("index.md", "# VeloChatX\n\nPlugin documentation.\n");

        Path plugins = root.resolve("nitrowater");
        Files.createDirectories(plugins.resolve("architecture"));
        Files.writeString(plugins.resolve("architecture/overview.md"),
                "# Overview\n\nPlatform architecture.\n", StandardCharsets.UTF_8);

        DocCorpusRegistry registry = new DocCorpusRegistry(properties(root));

        assertThat(registry.collections()).extracting(DocCollection::corpus)
                .containsExactly("nitrowater", "velochatx");
        assertThat(registry.collections())
                .filteredOn(collection -> collection.corpus().equals("velochatx"))
                .singleElement()
                .satisfies(collection -> assertThat(collection.description()).isEqualTo("test corpus"));
        assertThat(registry.collections())
                .filteredOn(collection -> collection.corpus().equals("nitrowater"))
                .singleElement()
                .satisfies(collection -> {
                    assertThat(collection.description()).isEmpty();
                    assertThat(collection.publicBaseUrl()).isEmpty();
                });
    }

    @Test
    void skipsDirectoriesWithoutMarkdown() throws IOException {
        writeCorpus("");
        write("index.md", "# VeloChatX\n\nPlugin documentation.\n");
        Files.createDirectories(root.resolve("empty-corpus"));

        assertThat(new DocCorpusRegistry(properties(root)).collections())
                .extracting(DocCollection::corpus)
                .containsExactly("velochatx");
    }

    @Test
    void scanReportsTheSameHashAsTheChunks() throws IOException {
        writeCorpus("");
        write("index.md", "# VeloChatX\n\nPlugin documentation.\n");

        DocChunkBuilder builder = builder();
        DocChunkBuilder.Result result = builder.build();
        List<DocChunkBuilder.DocStat> scanned = builder.scan();

        assertThat(scanned).hasSize(1);
        assertThat(scanned.getFirst().sha256()).hasSize(64)
                .isEqualTo(result.docs().getFirst().sha256());
        assertThat(scanned.getFirst().chunks()).isZero();
        assertThat(result.chunks()).allSatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry(DocChunkBuilder.META_CONTENT_SHA, scanned.getFirst().sha256()));
    }

    @Test
    void chunkIdsAreDeterministicAcrossBuilds() throws IOException {
        writeCorpus("");
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
        AiProperties properties = properties(root.resolve("missing"));

        DocChunkBuilder.Result result = new DocChunkBuilder(properties,
                new DocCorpusRegistry(properties)).build();

        assertThat(result.files()).isZero();
        assertThat(result.chunks()).isEmpty();
    }

    @Test
    void acceptsFileUriPrefixedLocation() throws IOException {
        writeCorpus("");
        write("index.md", """
                # Prefixed corpus

                Deployed corpora are usually written as file:/srv/docs/<corpus>.
                """);

        DocChunkBuilder builder = new DocChunkBuilder(properties(root),
                new DocCorpusRegistry(properties(root)));

        DocChunkBuilder.Result result = builder.build(List.of(
                new DocCollection("nitrowater", "file:" + corpus(), "")));

        assertThat(result.files()).isEqualTo(1);
        assertThat(result.chunks()).isNotEmpty();
    }

    @Test
    void buildsChunksForEveryDiscoveredCorpus() throws IOException {
        writeCorpus("");
        write("index.md", """
                # Filesystem corpus

                This document belongs to the temporary collection.
                """);

        Path other = root.resolve("nitrowater");
        Files.createDirectories(other);
        Files.writeString(other.resolve("index.md"), """
                # Platform corpus

                Markdown-only corpus without a published site.
                """, StandardCharsets.UTF_8);

        DocChunkBuilder.Result result = builder().build();

        assertThat(result.files()).isEqualTo(2);
        assertThat(result.chunks()).anySatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry(DocChunkBuilder.META_CORPUS, "velochatx"));
        assertThat(result.chunks()).anySatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry(DocChunkBuilder.META_CORPUS, "nitrowater"));
        assertThat(result.chunks())
                .filteredOn(chunk -> "nitrowater".equals(chunk.getMetadata().get(DocChunkBuilder.META_CORPUS)))
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get(DocChunkBuilder.META_URL))
                        .isEqualTo(""));
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

    private Path corpus() {
        return root.resolve("velochatx");
    }

    private void writeCorpus(String publicBaseUrl) throws IOException {
        Path dir = corpus();
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(DocCorpusRegistry.CORPUS_META_FILE), """
                description: test corpus
                public-base-url: %s
                """.formatted(publicBaseUrl), StandardCharsets.UTF_8);
    }

    private void write(String relative, String content) throws IOException {
        Path file = corpus().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static AiProperties properties(Path docsRoot) {
        return new AiProperties(true,
                500, 100, 40, 6, 0.35, 2000, 4, false, 20, docsRoot.toString());
    }
}
