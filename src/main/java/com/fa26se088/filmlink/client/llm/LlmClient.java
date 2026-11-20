package com.fa26se088.filmlink.client.llm;

import java.util.List;
import java.util.Objects;

/**
 * LLM API (SRS 4.1) cho domain {@code ai}: tạo embedding và gọi chat completion.
 *
 * <p>Service chỉ biết interface này; URL, header, định dạng JSON của nhà cung cấp nằm trong bản
 * thật ({@link OpenAiLlmClient}). Test dùng {@code @MockitoBean LlmClient}, không gọi Internet.
 *
 * <p>Lỗi mạng, HTTP khác 2xx, quá thời gian chờ hoặc response không đọc được đều ném {@link
 * LlmClientException}. Message của exception không chứa API key, không chứa nội dung câu hỏi.
 */
public interface LlmClient {

  /**
   * Client có đủ cấu hình để gọi nhà cung cấp hay không (ví dụ API key trống thì {@code false}).
   * Service kiểm trước khi gọi {@link #embed} / {@link #chat}; {@code false} thì dùng câu trả lời
   * dự phòng.
   */
  boolean isAvailable();

  /**
   * Tạo embedding cho từng đoạn văn bản, cùng thứ tự với {@code texts}.
   *
   * @param texts các đoạn văn bản, không rỗng
   * @param model tên model embedding (tham số hệ thống {@code EMBEDDING_MODEL})
   * @throws LlmClientException khi gọi thất bại hoặc số vector trả về khác số đoạn văn bản
   */
  EmbeddingResult embed(List<String> texts, String model);

  /**
   * Gọi chat completion với model chat cấu hình sẵn ({@code filmlink.ai.openai.chat-model}).
   *
   * @param messages hội thoại theo thứ tự thời gian, thường bắt đầu bằng một message {@code SYSTEM}
   * @throws LlmClientException khi gọi thất bại hoặc response không có nội dung
   */
  ChatResult chat(List<ChatMessage> messages);

  /** Vai trò của một message trong chat completion. */
  enum ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
  }

  /** Một message gửi cho model chat. */
  record ChatMessage(ChatRole role, String content) {

    public ChatMessage {
      Objects.requireNonNull(role, "role");
      Objects.requireNonNull(content, "content");
    }
  }

  /**
   * Kết quả embedding.
   *
   * @param vectors một vector cho mỗi đoạn văn bản, cùng thứ tự với input
   * @param model model nhà cung cấp báo đã dùng
   * @param promptTokens số token input nhà cung cấp tính; {@code null} nếu không có
   */
  record EmbeddingResult(List<float[]> vectors, String model, Integer promptTokens) {}

  /**
   * Kết quả chat completion.
   *
   * @param content nội dung trả lời của model
   * @param model model nhà cung cấp báo đã dùng
   * @param promptTokens số token input; {@code null} nếu không có
   * @param completionTokens số token output; {@code null} nếu không có
   */
  record ChatResult(String content, String model, Integer promptTokens, Integer completionTokens) {}
}
