package com.fa26se088.filmlink.dto.ai.response;

/**
 * BR-29: một Expert Article được trích dẫn trong câu trả lời.
 *
 * @param articleId id bài ({@code expert_articles.id})
 * @param title tiêu đề bài tại thời điểm trả lời
 */
public record ChatbotCitationResponse(Long articleId, String title) {}
