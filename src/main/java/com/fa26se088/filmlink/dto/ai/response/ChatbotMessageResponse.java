package com.fa26se088.filmlink.dto.ai.response;

import com.fa26se088.filmlink.entity.ai.enums.ChatbotMessageSender;
import java.time.Instant;
import java.util.List;

/**
 * UC19: một message trong lịch sử session chatbot.
 *
 * @param citations bài được trích dẫn (chỉ message {@code ASSISTANT}); rỗng nếu không có
 */
public record ChatbotMessageResponse(
    Long id,
    ChatbotMessageSender sender,
    String content,
    List<ChatbotCitationResponse> citations,
    Instant createdAt) {}
