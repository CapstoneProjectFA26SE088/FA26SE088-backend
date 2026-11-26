package com.fa26se088.filmlink.entity.ai;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.ai.enums.AiFeature;
import com.fa26se088.filmlink.entity.ai.enums.AiInteractionFeedback;
import com.fa26se088.filmlink.entity.ai.enums.AiInteractionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI Interaction (ERD #50), bảng {@code ai_interactions}: một lần gọi tính năng AI kèm kết quả.
 * Cũng là cơ sở tính quota chatbot theo ngày (UC19).
 *
 * <p>{@code request_payload} / {@code response_payload} / {@code error_message} không bao giờ chứa
 * API key.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_interactions")
public class AiInteraction extends BaseEntity {

  /** {@code null} với tính năng chạy nền do hệ thống kích hoạt. */
  @Column(name = "user_account_id")
  private Long userAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "feature", nullable = false, length = 30)
  private AiFeature feature;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AiInteractionStatus status;

  @Column(name = "is_fallback", nullable = false)
  private boolean fallback;

  @Column(name = "model_name", length = 100)
  private String modelName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_payload")
  private Map<String, Object> requestPayload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload")
  private Map<String, Object> responsePayload;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  @Column(name = "completion_tokens")
  private Integer completionTokens;

  @Enumerated(EnumType.STRING)
  @Column(name = "feedback", length = 20)
  private AiInteractionFeedback feedback;

  @Column(name = "feedback_note", length = 500)
  private String feedbackNote;
}
