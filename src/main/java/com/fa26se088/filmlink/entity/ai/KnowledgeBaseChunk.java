package com.fa26se088.filmlink.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Knowledge Base Entry (ERD #46), bảng {@code knowledge_base_chunks}: một đoạn của Expert Article đã
 * duyệt kèm embedding (BR-29).
 *
 * <p>Cột {@code embedding} ({@code public.vector}) <b>cố ý không map</b>: không cần module vector
 * của Hibernate, {@code ddl-auto: validate} chỉ kiểm cột được map. Ghi và tìm kiếm bằng native query
 * trong {@code KnowledgeBaseChunkRepository} (vector truyền dạng chuỗi {@code [x1,x2,...]}). Vì cột
 * đó {@code NOT NULL}, entity này {@link Immutable} và không bao giờ được {@code save}.
 *
 * <p>Bảng không có {@code updated_at} nên không kế thừa {@code BaseEntity}.
 */
@Getter
@Entity
@Immutable
@Table(name = "knowledge_base_chunks")
public class KnowledgeBaseChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "expert_article_id", nullable = false)
  private Long expertArticleId;

  @Column(name = "chunk_index", nullable = false)
  private Short chunkIndex;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "token_count", nullable = false)
  private Integer tokenCount;

  @Column(name = "embedding_model", nullable = false, length = 100)
  private String embeddingModel;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
