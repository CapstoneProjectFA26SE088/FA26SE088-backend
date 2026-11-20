package com.fa26se088.filmlink.repository.ai;

import com.fa26se088.filmlink.entity.ai.KnowledgeBaseChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Knowledge Base (BR-29). Cột {@code embedding} không map vào entity: ghi và tìm bằng native query,
 * vector truyền dạng chuỗi {@code [x1,x2,...]} rồi cast sang {@code public.vector}.
 *
 * <p>pgvector nằm ở schema {@code public}, ngoài search_path của app (ghi chú đầu migration {@code
 * V20261117_2200}): kiểu viết {@code public.vector}, toán tử viết {@code OPERATOR(public.<=>)}.
 */
public interface KnowledgeBaseChunkRepository extends JpaRepository<KnowledgeBaseChunk, Long> {

  /**
   * Model embedding duy nhất mà query và index HNSW hiện hỗ trợ ({@code
   * ix_knowledge_base_chunks_embedding_te3s}, 1536 chiều). Tham số {@code EMBEDDING_MODEL} phải bằng
   * giá trị này; đổi model phải có migration index mới và sửa hằng này (header C5).
   */
  String SUPPORTED_EMBEDDING_MODEL = "text-embedding-3-small";

  /**
   * UC19, BR-29: các chunk gần câu hỏi nhất theo cosine distance, chỉ của bài {@code PUBLISHED} và
   * {@code APPROVED}, với model embedding hiện hành.
   *
   * <p>Biểu thức {@code ORDER BY} lặp đúng biểu thức của index HNSW {@code
   * ix_knowledge_base_chunks_embedding_te3s} ({@code embedding::public.vector(1536)} với {@code
   * public.vector_cosine_ops}) để planner dùng được index. Chiều 1536 gắn với {@code
   * text-embedding-3-small}; đổi model thì phải thêm index và query mới (header C5).
   *
   * <p>Model viết <b>literal</b> ({@link #SUPPORTED_EMBEDDING_MODEL}), không phải tham số: index là
   * partial index {@code WHERE embedding_model = 'text-embedding-3-small'}; với tham số, generic
   * plan của prepared statement không chứng minh được điều kiện đó và bỏ index (đã kiểm bằng {@code
   * EXPLAIN}).
   *
   * @param queryEmbedding vector câu hỏi dạng {@code [x1,x2,...]}
   * @param limit số chunk tối đa
   */
  @Query(
      value =
          "SELECT c.id AS chunkId,"
              + " c.expert_article_id AS expertArticleId,"
              + " a.title AS articleTitle,"
              + " c.content AS content"
              + " FROM knowledge_base_chunks c"
              + " JOIN expert_articles a ON a.id = c.expert_article_id"
              + " WHERE c.embedding_model = '"
              + SUPPORTED_EMBEDDING_MODEL
              + "'"
              + " AND a.status = 'PUBLISHED'"
              + " AND a.kb_status = 'APPROVED'"
              + " ORDER BY c.embedding::public.vector(1536) OPERATOR(public.<=>)"
              + " CAST(:q AS public.vector(1536))"
              + " LIMIT :limit",
      nativeQuery = true)
  List<RetrievedChunk> findNearest(@Param("q") String queryEmbedding, @Param("limit") int limit);

  /** Xoá chunk cũ của một bài cho một model trước khi ghi lại. */
  @Modifying
  @Query(
      value =
          "DELETE FROM knowledge_base_chunks"
              + " WHERE expert_article_id = :articleId AND embedding_model = :model",
      nativeQuery = true)
  int deleteByArticleAndModel(@Param("articleId") Long articleId, @Param("model") String model);

  /**
   * Ghi một chunk kèm embedding.
   *
   * @param embedding vector dạng {@code [x1,x2,...]}
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO knowledge_base_chunks
              (expert_article_id, chunk_index, content, token_count, embedding_model, embedding)
          VALUES (:articleId, :chunkIndex, :content, :tokenCount, :model,
                  CAST(:embedding AS public.vector))
          """,
      nativeQuery = true)
  int insertChunk(
      @Param("articleId") Long articleId,
      @Param("chunkIndex") short chunkIndex,
      @Param("content") String content,
      @Param("tokenCount") int tokenCount,
      @Param("model") String model,
      @Param("embedding") String embedding);

  /** Một chunk tìm được cho câu hỏi, kèm tiêu đề bài để trích dẫn. */
  interface RetrievedChunk {

    Long getChunkId();

    Long getExpertArticleId();

    String getArticleTitle();

    String getContent();
  }
}
