package cn.nitrowater.autogen.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Minimal client for the DeepSeek Chat Completions API (OpenAI-compatible).
 *
 * <p>Only the JDK HTTP client plus Jackson 3 (the stack the rest of the project
 * uses) are needed, so there is no heavyweight SDK dependency. The API key is
 * read from the {@code DEEPSEEK_API_KEY} environment variable by the CLI and
 * passed in here.
 */
public final class LlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public LlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public LlmClient(String apiKey, String baseUrl, String model) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.baseUrl = (baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
                .replaceAll("/+$", "");
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    /** Sends a chat request and returns the assistant message content. */
    public String chat(String systemPrompt, String userPrompt)
            throws IOException, InterruptedException {
        ObjectNode body = NODES.objectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        addMessage(messages, "system", systemPrompt);
        addMessage(messages, "user", userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() / 100 != 2) {
            throw new IOException("DeepSeek API error " + response.statusCode() + ": "
                    + truncate(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asString().isBlank()) {
            throw new IOException("Unexpected DeepSeek response: " + truncate(response.body()));
        }
        return content.asString();
    }

    public String model() {
        return model;
    }

    public String endpoint() {
        return baseUrl + "/chat/completions";
    }

    private static void addMessage(ArrayNode messages, String role, String content) {
        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.put("content", content);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 600 ? text : text.substring(0, 600) + "...(truncated)";
    }
}
