package com.fa26se088.filmlink.dto.ai.response;

import java.util.List;

/**
 * UC19: câu trả lời của Advisory Chatbot.
 *
 * @param sessionId session chứa câu hỏi (mới tạo nếu request không có {@code sessionId})
 * @param answer câu trả lời, tiếng Việt
 * @param citations bài được trích dẫn, tối đa {@code CHATBOT_MAX_CITATIONS}; rỗng khi dự phòng hoặc
 *     từ chối
 * @param fallback {@code true} khi LLM không khả dụng / lỗi và đây là câu trả lời dự phòng (UH-08)
 */
public record ChatbotAnswerResponse(
    Long sessionId, String answer, List<ChatbotCitationResponse> citations, boolean fallback) {}
