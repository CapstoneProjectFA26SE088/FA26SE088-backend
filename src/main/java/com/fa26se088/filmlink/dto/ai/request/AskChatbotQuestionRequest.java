package com.fa26se088.filmlink.dto.ai.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * UC19: câu hỏi gửi Advisory Chatbot.
 *
 * @param question câu hỏi, 5–1000 ký tự ({@code ck_chatbot_messages_question})
 * @param sessionId session đang hỏi tiếp; {@code null} thì tạo session mới
 */
public record AskChatbotQuestionRequest(
    @Schema(example = "Làm sao push Portra 400 một stop?")
        @NotBlank
        @Size(min = 5, max = 1000, message = "Câu hỏi phải dài từ 5 đến 1000 ký tự.")
        String question,
    @Positive Long sessionId) {}
