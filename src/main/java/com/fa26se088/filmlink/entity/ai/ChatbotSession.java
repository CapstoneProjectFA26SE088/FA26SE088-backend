package com.fa26se088.filmlink.entity.ai;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Một cuộc hội thoại với Advisory Chatbot (UC19), bảng {@code chatbot_sessions}. */
@Getter
@Setter
@Entity
@Table(name = "chatbot_sessions")
public class ChatbotSession extends BaseEntity {

  /** Chủ session; chỉ chủ session đọc / hỏi tiếp được. */
  @Column(name = "user_account_id", nullable = false)
  private Long userAccountId;

  /** 150 ký tự đầu của câu hỏi đầu tiên. */
  @Column(name = "title", length = 150)
  private String title;

  @Column(name = "last_message_at", nullable = false)
  private Instant lastMessageAt;
}
