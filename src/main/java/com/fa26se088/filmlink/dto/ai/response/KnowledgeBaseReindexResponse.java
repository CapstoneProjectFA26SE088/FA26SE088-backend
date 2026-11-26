package com.fa26se088.filmlink.dto.ai.response;

/**
 * Kết quả lập chỉ mục lại Knowledge Base.
 *
 * @param articleCount số Expert Article đã lập chỉ mục
 * @param chunkCount tổng số chunk đã ghi
 * @param embeddingModel model embedding đã dùng ({@code EMBEDDING_MODEL})
 */
public record KnowledgeBaseReindexResponse(int articleCount, int chunkCount, String embeddingModel) {}
