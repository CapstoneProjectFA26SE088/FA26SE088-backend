package com.fa26se088.filmlink.mapper.ai;

import com.fa26se088.filmlink.dto.ai.response.ChatbotCitationResponse;
import com.fa26se088.filmlink.dto.ai.response.ChatbotMessageResponse;
import com.fa26se088.filmlink.entity.ai.ChatbotMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Chuyển message chatbot sang DTO và chuyển trích dẫn giữa DTO và jsonb.
 *
 * <p>Trong cột {@code chatbot_messages.citations} mỗi phần tử là {@code {"expertArticleId": n,
 * "title": "..."}} (comment cột trong migration); API trả {@code {articleId, title}}.
 */
@Component
public class ChatbotMapper {

  static final String KEY_ARTICLE_ID = "expertArticleId";
  static final String KEY_TITLE = "title";

  public ChatbotMessageResponse toResponse(ChatbotMessage message) {
    return new ChatbotMessageResponse(
        message.getId(),
        message.getSender(),
        message.getContent(),
        fromJson(message.getCitations()),
        message.getCreatedAt());
  }

  /** Trích dẫn sang dạng lưu jsonb; danh sách rỗng thành {@code null}. */
  public List<Map<String, Object>> toJson(List<ChatbotCitationResponse> citations) {
    if (citations == null || citations.isEmpty()) {
      return null;
    }
    return citations.stream()
        .<Map<String, Object>>map(
            citation -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put(KEY_ARTICLE_ID, citation.articleId());
              item.put(KEY_TITLE, citation.title());
              return item;
            })
        .toList();
  }

  public List<ChatbotCitationResponse> fromJson(List<Map<String, Object>> citations) {
    if (citations == null) {
      return List.of();
    }
    return citations.stream()
        .map(
            item ->
                new ChatbotCitationResponse(
                    item.get(KEY_ARTICLE_ID) instanceof Number id ? id.longValue() : null,
                    item.get(KEY_TITLE) == null ? null : item.get(KEY_TITLE).toString()))
        .toList();
  }
}
