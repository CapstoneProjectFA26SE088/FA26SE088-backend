package com.fa26se088.filmlink.service.ai;

import com.fa26se088.filmlink.dto.ai.response.KnowledgeBaseReindexResponse;

/**
 * BR-29: lập chỉ mục Knowledge Base của Advisory Chatbot từ các Expert Article đã được Moderator
 * duyệt ({@code kb_status = APPROVED}) và đang hiển thị ({@code status = PUBLISHED}).
 *
 * <p>Luồng duyệt bài (UC44) chưa có, nên Admin gọi lập chỉ mục bằng tay (demo, test).
 */
public interface KnowledgeBaseIndexer {

  /**
   * BR-29: lập chỉ mục lại mọi bài APPROVED + PUBLISHED với model embedding hiện hành ({@code
   * EMBEDDING_MODEL}). Mỗi bài: chia đoạn → embed (ngoài transaction) → trong một transaction xoá
   * chunk cũ của bài cho model đó rồi ghi chunk mới. Ghi audit {@code KNOWLEDGE_BASE_REINDEXED}.
   *
   * @throws com.fa26se088.filmlink.exception.BusinessException {@code AI_PROVIDER_UNAVAILABLE} (503)
   *     khi LLM chưa cấu hình hoặc lỗi; các bài đã ghi trước đó được giữ
   */
  KnowledgeBaseReindexResponse reindexAll();
}
