package cn.nitrowater.server.ai.tool;

import cn.nitrowater.server.config.ai.WebSearchProperties;
import cn.nitrowater.server.service.ai.tool.DeepSeekWebSearchProvider;
import cn.nitrowater.server.service.ai.model.tool.WebSearchResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the DeepSeek {@code /v1/tools/search} request shape, retry policy and result parsing. */
class DeepSeekWebSearchProviderTest {

    @Test
    void sendsQueryAndParsesResultsPreferringSummary() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tools/search", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {"results":[
                      {"rerank_score":2.4,"title":"DeepSeek V4 released","url":"https://example.com/a",
                       "snippet":"short preview","summary":"long model-ready summary",
                       "content":"","site_name":"Example","date":"2026-06-01T00:00:00+00:00","authority":3},
                      {"rerank_score":1.1,"title":"No summary","url":"https://example.com/b","snippet":"only snippet"},
                      {"title":"No URL","snippet":"dropped"}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
        server.start();

        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/tools/search";
            DeepSeekWebSearchProvider provider = new DeepSeekWebSearchProvider(new WebSearchProperties(
                    true, "test-key", endpoint, true, 3, 3, 1, 0, 5_000));

            List<WebSearchResult> results = provider.search("DeepSeek V4", 99);

            assertThat(provider.enabled()).isTrue();
            assertThat(authorization.get()).isEqualTo("Bearer test-key");
            assertThat(requestBody.get())
                    .contains("\"query\":\"DeepSeek V4\"")
                    .contains("\"count\":10")
                    .contains("\"need_content\":false");
            assertThat(results).hasSize(2);
            assertThat(results.get(0).url()).isEqualTo("https://example.com/a");
            assertThat(results.get(0).snippet()).isEqualTo("long model-ready summary");
            assertThat(results.get(1).snippet()).isEqualTo("only snippet");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesOnceWhenTheServerFailsTransiently() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tools/search", exchange -> {
            int call = calls.incrementAndGet();
            byte[] body = call == 1
                    ? "{}".getBytes(StandardCharsets.UTF_8)
                    : """
                      {"results":[{"title":"Recovered","url":"https://example.com/recovered","summary":"ok"}]}
                      """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(call == 1 ? 500 : 200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
        server.start();

        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/tools/search";
            DeepSeekWebSearchProvider provider = new DeepSeekWebSearchProvider(new WebSearchProperties(
                    true, "test-key", endpoint, true, 3, 3, 2, 0, 5_000));

            List<WebSearchResult> results = provider.search("transient", 3);

            assertThat(calls.get()).isEqualTo(2);
            assertThat(results).extracting(WebSearchResult::url).containsExactly("https://example.com/recovered");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryClientErrors() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tools/search", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
        server.start();

        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/tools/search";
            DeepSeekWebSearchProvider provider = new DeepSeekWebSearchProvider(new WebSearchProperties(
                    true, "test-key", endpoint, true, 3, 3, 2, 0, 5_000));

            assertThatThrownBy(() -> provider.search("bad", 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("after 1 attempt");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledWithoutApiKey() {
        DeepSeekWebSearchProvider provider = new DeepSeekWebSearchProvider(new WebSearchProperties(
                true, " ", "", true, 3, 3, 2, 0, 5_000));

        assertThat(provider.enabled()).isFalse();
        assertThat(provider.search("anything", 5)).isEmpty();
    }
}
