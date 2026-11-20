package com.fa26se088.filmlink.client.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình OpenAI, prefix {@code filmlink.ai.openai}. {@code apiKey} là bí mật, chỉ đọc từ biến môi
 * trường {@code OPENAI_API_KEY}, mặc định trống: khi đó {@link OpenAiLlmClient#isAvailable()} trả
 * {@code false} và chatbot trả câu trả lời dự phòng (build / test không bao giờ cần key).
 *
 * <p>Model embedding không nằm ở đây: đọc từ tham số hệ thống {@code EMBEDDING_MODEL}.
 *
 * @param apiKey API key ({@code OPENAI_API_KEY}), có thể trống
 * @param chatModel model chat ({@code OPENAI_CHAT_MODEL}, mặc định {@code gpt-4o-mini})
 * @param baseUrl gốc API, không có {@code /} ở cuối ({@code OPENAI_BASE_URL})
 * @param timeout thời gian chờ tối đa cho kết nối và cho mỗi request
 */
@ConfigurationProperties(prefix = "filmlink.ai.openai")
public record OpenAiProperties(String apiKey, String chatModel, String baseUrl, Duration timeout) {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

  public OpenAiProperties {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      timeout = DEFAULT_TIMEOUT;
    }
  }

  /** Có key và URL để gọi API. */
  public boolean isConfigured() {
    return hasText(apiKey) && hasText(baseUrl) && hasText(chatModel);
  }

  /** {@code toString} mặc định của record in cả key: che đi. */
  @Override
  public String toString() {
    return "OpenAiProperties[apiKey="
        + (hasText(apiKey) ? "***" : "")
        + ", chatModel="
        + chatModel
        + ", baseUrl="
        + baseUrl
        + ", timeout="
        + timeout
        + "]";
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
