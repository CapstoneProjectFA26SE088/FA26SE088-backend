package com.fa26se088.filmlink.client.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bản thật của {@link LlmClient}: gọi OpenAI REST API bằng {@link HttpClient} của JDK và Jackson có
 * sẵn (không thêm SDK).
 *
 * <ul>
 *   <li>{@code POST {base-url}/embeddings} — body {@code {model, input: [...]}}.
 *   <li>{@code POST {base-url}/chat/completions} — body {@code {model, messages, temperature}},
 *       {@code temperature} = {@value #CHAT_TEMPERATURE} để câu trả lời bám ngữ cảnh.
 * </ul>
 *
 * <p>Timeout kết nối và timeout mỗi request lấy từ {@link OpenAiProperties#timeout()} (mặc định 20
 * giây). Không tự retry: lỗi trả ngay {@link LlmClientException} để service dùng câu trả lời dự
 * phòng (UH-08).
 *
 * <p>Không log API key, không log nội dung câu hỏi / ngữ cảnh. Log WARN chỉ có endpoint, mã HTTP và
 * loại lỗi.
 */
@Slf4j
@Component
public class OpenAiLlmClient implements LlmClient {

  static final double CHAT_TEMPERATURE = 0.2;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final OpenAiProperties properties;
  private final HttpClient httpClient;

  public OpenAiLlmClient(OpenAiProperties properties) {
    this.properties = properties;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public boolean isAvailable() {
    return properties.isConfigured();
  }

  @Override
  public EmbeddingResult embed(List<String> texts, String model) {
    if (texts == null || texts.isEmpty()) {
      throw new IllegalArgumentException("texts must not be empty");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("input", texts);
    JsonNode root = post("/embeddings", body);

    JsonNode data = root.path("data");
    if (!data.isArray() || data.size() != texts.size()) {
      throw new LlmClientException(
          "Embedding response has "
              + (data.isArray() ? data.size() : 0)
              + " vectors, expected "
              + texts.size());
    }
    float[][] ordered = new float[texts.size()][];
    for (int i = 0; i < data.size(); i++) {
      JsonNode item = data.get(i);
      int index = item.path("index").isNumber() ? item.path("index").intValue() : i;
      JsonNode embedding = item.path("embedding");
      if (index < 0 || index >= ordered.length || !embedding.isArray() || embedding.isEmpty()) {
        throw new LlmClientException("Embedding response item " + i + " is invalid");
      }
      float[] vector = new float[embedding.size()];
      for (int d = 0; d < vector.length; d++) {
        JsonNode value = embedding.get(d);
        if (!value.isNumber()) {
          throw new LlmClientException("Embedding response item " + i + " is not numeric");
        }
        vector[d] = (float) value.doubleValue();
      }
      ordered[index] = vector;
    }
    List<float[]> vectors = new ArrayList<>(ordered.length);
    for (float[] vector : ordered) {
      if (vector == null) {
        throw new LlmClientException("Embedding response is missing an index");
      }
      vectors.add(vector);
    }
    return new EmbeddingResult(
        vectors, textOr(root.path("model"), model), intOrNull(root.path("usage").path("prompt_tokens")));
  }

  @Override
  public ChatResult chat(List<ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty");
    }
    List<Map<String, String>> payloadMessages = new ArrayList<>(messages.size());
    for (ChatMessage message : messages) {
      payloadMessages.add(
          Map.of("role", message.role().name().toLowerCase(Locale.ROOT), "content", message.content()));
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.chatModel());
    body.put("messages", payloadMessages);
    body.put("temperature", CHAT_TEMPERATURE);
    JsonNode root = post("/chat/completions", body);

    JsonNode content = root.path("choices").path(0).path("message").path("content");
    if (!content.isString() || content.stringValue().isBlank()) {
      throw new LlmClientException("Chat completion response has no content");
    }
    JsonNode usage = root.path("usage");
    return new ChatResult(
        content.stringValue().strip(),
        textOr(root.path("model"), properties.chatModel()),
        intOrNull(usage.path("prompt_tokens")),
        intOrNull(usage.path("completion_tokens")));
  }

  /** Gửi một request JSON, trả body đã parse; mọi lỗi thành {@link LlmClientException}. */
  private JsonNode post(String path, Map<String, Object> body) {
    if (!isAvailable()) {
      throw new LlmClientException("LLM provider is not configured");
    }
    String endpoint = stripTrailingSlash(properties.baseUrl()) + path;
    HttpRequest request;
    try {
      request =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(properties.timeout())
              .header("Authorization", "Bearer " + properties.apiKey().strip())
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
              .build();
    } catch (IllegalArgumentException | JacksonException ex) {
      throw new LlmClientException("Cannot build LLM request for " + path, ex);
    }

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (HttpTimeoutException ex) {
      log.warn("LLM call {} timed out after {}", path, properties.timeout());
      throw new LlmClientException("LLM call " + path + " timed out", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new LlmClientException("LLM call " + path + " was interrupted", ex);
    } catch (IOException ex) {
      log.warn("LLM call {} failed: {}", path, ex.getClass().getSimpleName());
      throw new LlmClientException(
          "LLM call " + path + " failed: " + ex.getClass().getSimpleName(), ex);
    }

    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      // Không đưa body vào message/log: body lỗi của OpenAI có thể nhắc lại một phần API key.
      log.warn("LLM call {} returned HTTP {}", path, status);
      throw new LlmClientException("LLM call " + path + " returned HTTP " + status);
    }
    try {
      return JSON.readTree(response.body());
    } catch (JacksonException ex) {
      throw new LlmClientException("LLM call " + path + " returned an unreadable body", ex);
    }
  }

  private static String stripTrailingSlash(String url) {
    String trimmed = url.strip();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  private static String textOr(JsonNode node, String fallback) {
    return node.isString() && !node.stringValue().isBlank() ? node.stringValue() : fallback;
  }

  private static Integer intOrNull(JsonNode node) {
    return node.isNumber() ? node.intValue() : null;
  }
}
