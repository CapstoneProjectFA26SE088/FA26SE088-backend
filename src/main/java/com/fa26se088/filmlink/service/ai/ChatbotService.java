package com.fa26se088.filmlink.service.ai;

import com.fa26se088.filmlink.dto.ai.request.AskChatbotQuestionRequest;
import com.fa26se088.filmlink.dto.ai.response.ChatbotAnswerResponse;
import com.fa26se088.filmlink.dto.ai.response.ChatbotMessageResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import org.springframework.data.domain.Pageable;

/** Advisory Chatbot (UC19, F-23): tư vấn nhiếp ảnh phim dựa trên Knowledge Base (BR-29). */
public interface ChatbotService {

  /**
   * UC19, BR-29: trả lời câu hỏi <b>chỉ</b> từ các chunk của Expert Article đã duyệt và trích dẫn bài
   * (tối đa {@code CHATBOT_MAX_CITATIONS}).
   *
   * <ul>
   *   <li>LLM không khả dụng / lỗi → câu trả lời dự phòng, {@code fallback = true}, status {@code
   *       FALLBACK} (HTTP 200, UH-08).
   *   <li>Knowledge Base không có đoạn liên quan → câu trả lời cố định, status {@code REFUSED}, không
   *       trích dẫn.
   * </ul>
   *
   * <p>Không gọi LLM trong transaction DB. Mỗi câu hỏi ghi một dòng {@code ai_interactions} ({@code
   * ADVISORY_CHATBOT}) và hai dòng {@code chatbot_messages} (USER, ASSISTANT).
   *
   * @param accountId người hỏi (PHOTOGRAPHER hoặc PHOTOGRAPHY_EXPERT)
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code AI_CHAT_SESSION_NOT_FOUND}
   *     khi {@code sessionId} không tồn tại hoặc của người khác
   */
  ChatbotAnswerResponse ask(Long accountId, AskChatbotQuestionRequest request);

  /**
   * UC19: lịch sử một session của chính người xem, cũ trước (bỏ qua sort client gửi).
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code AI_CHAT_SESSION_NOT_FOUND}
   *     khi session không tồn tại hoặc của người khác
   */
  PageResponse<ChatbotMessageResponse> getMessages(
      Long accountId, Long sessionId, Pageable pageable);
}
