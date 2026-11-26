package com.fa26se088.filmlink.controller.ai;

import com.fa26se088.filmlink.dto.ai.response.KnowledgeBaseReindexResponse;
import com.fa26se088.filmlink.service.ai.KnowledgeBaseIndexer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin lập chỉ mục Knowledge Base của chatbot (BR-29) khi chưa có luồng duyệt bài (UC44). */
@RestController
@RequestMapping("/api/v1/admin/knowledge-base")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "AI - Knowledge Base (Admin)", description = "Lập chỉ mục Knowledge Base (BR-29)")
public class KnowledgeBaseAdminController {

  private final KnowledgeBaseIndexer knowledgeBaseIndexer;

  @PostMapping("/reindex")
  @Operation(
      summary = "[Admin] Lập chỉ mục lại Knowledge Base",
      description =
          "Embed lại mọi Expert Article APPROVED + PUBLISHED với EMBEDDING_MODEL hiện hành."
              + " LLM chưa cấu hình hoặc lỗi: 503 AI_PROVIDER_UNAVAILABLE.")
  public KnowledgeBaseReindexResponse reindex() {
    return knowledgeBaseIndexer.reindexAll();
  }
}
