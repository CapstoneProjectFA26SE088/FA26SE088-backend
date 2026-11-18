package com.fa26se088.filmlink.entity.ai;

import com.fa26se088.filmlink.entity.ai.enums.ChatbotMessageSender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một message trong session chatbot (UC19), bảng {@code chatbot_messages}.
 *
 * <p>Bảng không có {@code updated_at} nên <b>không</b> kế thừa {@code BaseEntity}; {@link
 * #createdAt} do service gán. Câu hỏi ({@code USER}) dài 5–1000 ký tự; trích dẫn chỉ có ở {@code
 * ASSISTANT}, tối đa 3 phần tử dạng {@code {"expertArticleId": n, "title": "..."}} (comment cột
 * trong migration).
 */
@Getter
@Setter
@Entity
@Table(name = "chatbot_messages")
public class ChatbotMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "chatbot_session_id", nullable = false)
  private Long chatbotSessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "sender", nullable = false, length = 10)
  private ChatbotMessageSender sender;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citations")
  private List<Map<String, Object>> citations;

  @Column(name = "ai_interaction_id")
  private Long aiInteractionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
