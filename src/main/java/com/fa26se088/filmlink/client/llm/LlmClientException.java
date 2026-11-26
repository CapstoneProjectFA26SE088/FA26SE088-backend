package com.fa26se088.filmlink.client.llm;

/**
 * Gọi LLM API thất bại: chưa cấu hình, lỗi mạng, quá thời gian chờ, HTTP khác 2xx, response không
 * đọc được.
 *
 * <p>Message chỉ mô tả loại lỗi và mã HTTP, đủ để ghi vào {@code ai_interactions.error_message}:
 * <b>không</b> chứa API key, <b>không</b> chứa body lỗi của nhà cung cấp (body lỗi của OpenAI có thể
 * nhắc lại một phần key), <b>không</b> chứa nội dung câu hỏi.
 */
public class LlmClientException extends RuntimeException {

  public LlmClientException(String message) {
    super(message);
  }

  public LlmClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
