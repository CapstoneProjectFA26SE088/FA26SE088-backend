package com.fa26se088.filmlink.entity.community;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleCategory;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleKbStatus;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;

/**
 * Expert Article (ERD #45), bảng {@code expert_articles} (UC42–UC44, BR-12, BR-29).
 *
 * <p>Hai trạng thái độc lập: {@link #status} (hiển thị công khai) và {@link #kbStatus} (vào
 * Knowledge Base sau khi Moderator duyệt).
 *
 * <p>Hiện chỉ <b>đọc</b> (T-12: chatbot lập chỉ mục bài đã duyệt). Luồng viết / duyệt bài (UC42,
 * UC44) chưa làm nên entity chưa có setter. Tác giả và người duyệt là id thuần, không
 * {@code @ManyToOne}.
 */
@Getter
@Entity
@Table(name = "expert_articles")
public class ExpertArticle extends BaseEntity {

  @Column(name = "author_account_id", nullable = false)
  private Long authorAccountId;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 20)
  private ExpertArticleCategory category;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ExpertArticleStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "kb_status", nullable = false, length = 20)
  private ExpertArticleKbStatus kbStatus;

  @Column(name = "kb_submitted_at")
  private Instant kbSubmittedAt;

  @Column(name = "kb_reviewed_by_account_id")
  private Long kbReviewedByAccountId;

  @Column(name = "kb_reviewed_at")
  private Instant kbReviewedAt;

  @Column(name = "kb_rejection_reason", length = 500)
  private String kbRejectionReason;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;
}
