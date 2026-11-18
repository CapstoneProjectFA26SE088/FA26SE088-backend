package com.fa26se088.filmlink.client.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fa26se088.filmlink.client.llm.LlmClient.ChatMessage;
import com.fa26se088.filmlink.client.llm.LlmClient.ChatResult;
import com.fa26se088.filmlink.client.llm.LlmClient.ChatRole;
import com.fa26se088.filmlink.client.llm.LlmClient.EmbeddingResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Client OpenAI gọi {@link HttpServer} trên 127.0.0.1 (base-url trỏ vào server test), không gọi
 * Internet, key là chuỗi giả.
 */
class OpenAiLlmClientTest {

  private static final String FAKE_KEY = "sk-test-not-a-real-key";

  private HttpServer server;
  private String baseUrl;

  private volatile int responseStatus = 200;
  private volatile String responseBody = "{}";
  private volatile long delayMillis;

  private volatile String lastMethod;
  private volatile String lastPath;
  private volatile String lastAuthorization;
  private volatile String lastBody;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newFixedThreadPool(2));
    server.createContext("/", this::handle);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void embed_postsToEmbeddingsWithBearerKey_andReadsVectorsInIndexOrder() {
    responseBody =
        """
        {"object":"list","model":"text-embedding-3-small",
         "data":[{"object":"embedding","index":1,"embedding":[0.5,-0.25]},
                 {"object":"embedding","index":0,"embedding":[1.0,0.0]}],
         "usage":{"prompt_tokens":7,"total_tokens":7}}
        """;

    EmbeddingResult result = client(FAKE_KEY).embed(List.of("một", "hai"), "text-embedding-3-small");

    assertThat(lastMethod).isEqualTo("POST");
    assertThat(lastPath).isEqualTo("/v1/embeddings");
    assertThat(lastAuthorization).isEqualTo("Bearer " + FAKE_KEY);
    assertThat(lastBody).contains("\"model\":\"text-embedding-3-small\"").contains("\"input\":[\"một\",\"hai\"]");
    assertThat(result.vectors()).hasSize(2);
    assertThat(result.vectors().get(0)).containsExactly(1.0f, 0.0f);
    assertThat(result.vectors().get(1)).containsExactly(0.5f, -0.25f);
    assertThat(result.model()).isEqualTo("text-embedding-3-small");
    assertThat(result.promptTokens()).isEqualTo(7);
  }

  @Test
  void chat_postsToChatCompletions_andReadsContentAndUsage() {
    responseBody =
        """
        {"id":"chatcmpl-1","model":"gpt-4o-mini-2024-07-18",
         "choices":[{"index":0,"message":{"role":"assistant","content":"  Tăng thời gian tráng.  "},
                     "finish_reason":"stop"}],
         "usage":{"prompt_tokens":120,"completion_tokens":15,"total_tokens":135}}
        """;

    ChatResult result =
        client(FAKE_KEY)
            .chat(
                List.of(
                    new ChatMessage(ChatRole.SYSTEM, "Chỉ dùng ngữ cảnh."),
                    new ChatMessage(ChatRole.USER, "Push một stop?")));

    assertThat(lastPath).isEqualTo("/v1/chat/completions");
    assertThat(lastAuthorization).isEqualTo("Bearer " + FAKE_KEY);
    assertThat(lastBody)
        .contains("\"model\":\"gpt-4o-mini\"")
        .contains("\"role\":\"system\"")
        .contains("\"role\":\"user\"")
        .contains("\"temperature\":0.2");
    assertThat(result.content()).isEqualTo("Tăng thời gian tráng.");
    assertThat(result.model()).isEqualTo("gpt-4o-mini-2024-07-18");
    assertThat(result.promptTokens()).isEqualTo(120);
    assertThat(result.completionTokens()).isEqualTo(15);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 429, 500, 503})
  void non2xx_throwsClientExceptionWithoutKeyOrBody(int status) {
    responseStatus = status;
    responseBody = "{\"error\":{\"message\":\"Incorrect API key provided: " + FAKE_KEY + "\"}}";

    assertThatThrownBy(() -> client(FAKE_KEY).embed(List.of("x"), "text-embedding-3-small"))
        .isInstanceOf(LlmClientException.class)
        .hasMessageContaining("HTTP " + status)
        .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(FAKE_KEY));
    assertThatThrownBy(
            () -> client(FAKE_KEY).chat(List.of(new ChatMessage(ChatRole.USER, "câu hỏi bí mật"))))
        .isInstanceOf(LlmClientException.class)
        .satisfies(
            ex -> assertThat(ex.getMessage()).doesNotContain(FAKE_KEY).doesNotContain("câu hỏi"));
  }

  @Test
  void timeout_throwsClientException() {
    delayMillis = 1_500;

    OpenAiLlmClient client =
        new OpenAiLlmClient(new OpenAiProperties(FAKE_KEY, "gpt-4o-mini", baseUrl, Duration.ofMillis(300)));

    assertThatThrownBy(() -> client.chat(List.of(new ChatMessage(ChatRole.USER, "hello"))))
        .isInstanceOf(LlmClientException.class)
        .hasMessageContaining("timed out");
  }

  @Test
  void connectionRefused_throwsClientException() {
    server.stop(0);

    assertThatThrownBy(() -> client(FAKE_KEY).embed(List.of("x"), "text-embedding-3-small"))
        .isInstanceOf(LlmClientException.class);
  }

  @Test
  void malformedResponses_throwClientException() {
    responseBody = "not json";
    assertThatThrownBy(() -> client(FAKE_KEY).embed(List.of("x"), "m"))
        .isInstanceOf(LlmClientException.class);

    responseBody = "{\"data\":[]}";
    assertThatThrownBy(() -> client(FAKE_KEY).embed(List.of("x"), "m"))
        .isInstanceOf(LlmClientException.class);

    responseBody = "{\"choices\":[]}";
    assertThatThrownBy(() -> client(FAKE_KEY).chat(List.of(new ChatMessage(ChatRole.USER, "hi"))))
        .isInstanceOf(LlmClientException.class);
  }

  @Test
  void blankKey_isUnavailable_andNeverCallsServer() {
    OpenAiLlmClient blank = client("");
    OpenAiLlmClient missing = client(null);

    assertThat(blank.isAvailable()).isFalse();
    assertThat(missing.isAvailable()).isFalse();
    assertThat(client(FAKE_KEY).isAvailable()).isTrue();
    assertThatThrownBy(() -> blank.embed(List.of("x"), "m")).isInstanceOf(LlmClientException.class);
    assertThat(lastPath).isNull();
  }

  @Test
  void propertiesToString_masksKey() {
    OpenAiProperties properties = new OpenAiProperties(FAKE_KEY, "gpt-4o-mini", baseUrl, null);

    assertThat(properties.toString()).doesNotContain(FAKE_KEY);
    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(20));
  }

  private OpenAiLlmClient client(String key) {
    return new OpenAiLlmClient(
        new OpenAiProperties(key, "gpt-4o-mini", baseUrl, Duration.ofSeconds(5)));
  }

  private void handle(HttpExchange exchange) throws IOException {
    lastMethod = exchange.getRequestMethod();
    lastPath = exchange.getRequestURI().getPath();
    lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
    lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (delayMillis > 0) {
      try {
        Thread.sleep(delayMillis);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    try {
      exchange.sendResponseHeaders(responseStatus, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    } catch (IOException ignored) {
      // client đã đóng kết nối (ca timeout)
    } finally {
      exchange.close();
    }
  }
}
