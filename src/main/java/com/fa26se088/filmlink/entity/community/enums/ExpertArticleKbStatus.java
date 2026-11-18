package com.fa26se088.filmlink.entity.community.enums;

/**
 * Trạng thái của Expert Article với Knowledge Base (UC44, BR-29), khớp {@code
 * ck_expert_articles_kb_status}. Chỉ bài {@code APPROVED} (và đang {@code PUBLISHED}) được đưa vào
 * Knowledge Base của chatbot. {@code WITHDRAWN}: rút khỏi Knowledge Base sau khi đã duyệt.
 */
public enum ExpertArticleKbStatus {
  NOT_SUBMITTED,
  PENDING_REVIEW,
  APPROVED,
  REJECTED,
  WITHDRAWN
}
