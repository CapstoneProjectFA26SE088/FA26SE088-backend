package com.fa26se088.filmlink.service.ai.impl;

import com.fa26se088.filmlink.client.llm.LlmClient;
import com.fa26se088.filmlink.client.llm.LlmClient.EmbeddingResult;
import com.fa26se088.filmlink.client.llm.LlmClientException;
import com.fa26se088.filmlink.dto.ai.response.KnowledgeBaseReindexResponse;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleKbStatus;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleStatus;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.repository.ai.KnowledgeBaseChunkRepository;
import com.fa26se088.filmlink.repository.community.ExpertArticleRepository;
import com.fa26se088.filmlink.service.ai.KnowledgeBaseIndexer;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lập chỉ mục Knowledge Base (BR-29).
 *
 * <p>Không có {@code @Transactional} ở method public: lời gọi embedding (tới 20 giây) không được
 * giữ connection / transaction DB. Đọc bài và ghi chunk dùng {@link TransactionTemplate} ngắn, một
 * transaction ghi cho mỗi bài.
 *
 * <p>Đọc {@code expert_articles} qua {@link ExpertArticleRepository} của domain {@code community}
 * (chỉ đọc): T-12 chỉ cho phép thêm entity + repository ở {@code community}, chưa có service.
 */
@Slf4j
@Service
public class KnowledgeBaseIndexerImpl implements KnowledgeBaseIndexer {

  private final LlmClient llmClient;
  private final ExpertArticleRepository expertArticleRepository;
  private final KnowledgeBaseChunkRepository knowledgeBaseChunkRepository;
  private final SystemParameterService systemParameterService;
  private final AuditLogService auditLogService;
  private final TransactionTemplate readTx;
  private final TransactionTemplate writeTx;

  public KnowledgeBaseIndexerImpl(
      LlmClient llmClient,
      ExpertArticleRepository expertArticleRepository,
      KnowledgeBaseChunkRepository knowledgeBaseChunkRepository,
      SystemParameterService systemParameterService,
      AuditLogService auditLogService,
      PlatformTransactionManager transactionManager) {
    this.llmClient = llmClient;
    this.expertArticleRepository = expertArticleRepository;
    this.knowledgeBaseChunkRepository = knowledgeBaseChunkRepository;
    this.systemParameterService = systemParameterService;
    this.auditLogService = auditLogService;
    this.readTx = new TransactionTemplate(transactionManager);
    this.readTx.setReadOnly(true);
    this.writeTx = new TransactionTemplate(transactionManager);
  }

  /** BR-29: chỉ bài APPROVED + PUBLISHED vào Knowledge Base. */
  @Override
  public KnowledgeBaseReindexResponse reindexAll() {
    if (!llmClient.isAvailable()) {
      throw unavailable("Dịch vụ AI chưa được cấu hình, chưa lập chỉ mục được Knowledge Base.");
    }
    String model = systemParameterService.getString(SystemParameterKey.EMBEDDING_MODEL);
    if (!KnowledgeBaseChunkRepository.SUPPORTED_EMBEDDING_MODEL.equals(model)) {
      log.error(
          "EMBEDDING_MODEL '{}' is not supported by the Knowledge Base query/index (expected '{}');"
              + " reindex refused",
          model,
          KnowledgeBaseChunkRepository.SUPPORTED_EMBEDDING_MODEL);
      throw unavailable(
          "Cấu hình model embedding không khớp Knowledge Base, chưa lập chỉ mục được."
              + " Vui lòng báo quản trị hệ thống.");
    }

    List<ArticleText> articles =
        readTx.execute(
            status ->
                expertArticleRepository
                    .findByStatusAndKbStatusOrderByIdAsc(
                        ExpertArticleStatus.PUBLISHED, ExpertArticleKbStatus.APPROVED)
                    .stream()
                    .map(article -> new ArticleText(article.getId(), article.getContent()))
                    .toList());

    int articleCount = 0;
    int chunkCount = 0;
    for (ArticleText article : articles) {
      List<String> chunks = KnowledgeBaseText.split(article.content());
      if (chunks.isEmpty()) {
        continue;
      }
      if (chunks.size() > Short.MAX_VALUE) {
        throw new IllegalStateException("Expert Article " + article.id() + " has too many chunks");
      }
      List<float[]> vectors = embed(chunks, model, articleCount, articles.size());

      Integer written = writeTx.execute(status -> replaceChunks(article.id(), chunks, vectors, model));
      if (written != null && written > 0) {
        articleCount++;
        chunkCount += written;
      }
    }

    Map<String, Object> after = new LinkedHashMap<>();
    after.put("articleCount", articleCount);
    after.put("chunkCount", chunkCount);
    after.put("embeddingModel", model);
    writeTx.executeWithoutResult(
        status ->
            auditLogService.recordSuccess(
                new AuditEntry(
                    AuditAction.KNOWLEDGE_BASE_REINDEXED,
                    AuditTargetType.KNOWLEDGE_BASE_ENTRY,
                    null,
                    null,
                    null,
                    after)));
    log.info("Knowledge Base reindexed: {} articles, {} chunks, model {}", articleCount, chunkCount, model);
    return new KnowledgeBaseReindexResponse(articleCount, chunkCount, model);
  }

  /** Gọi embedding ngoài transaction; kiểm số vector và số chiều. */
  private List<float[]> embed(List<String> chunks, String model, int done, int total) {
    try {
      EmbeddingResult result = llmClient.embed(chunks, model);
      if (result.vectors().size() != chunks.size()) {
        throw new LlmClientException("Embedding count does not match chunk count");
      }
      for (float[] vector : result.vectors()) {
        if (vector.length != KnowledgeBaseText.EMBEDDING_DIMENSIONS) {
          throw new LlmClientException("Unexpected embedding dimension " + vector.length);
        }
      }
      return result.vectors();
    } catch (LlmClientException ex) {
      log.warn("Knowledge Base reindex stopped after {}/{} articles: {}", done, total, ex.getMessage());
      throw unavailable(
          "Dịch vụ AI đang lỗi, dừng lập chỉ mục sau "
              + done
              + "/"
              + total
              + " bài. Vui lòng thử lại sau.");
    }
  }

  /**
   * Một transaction: xoá chunk cũ của bài cho model hiện hành rồi ghi chunk mới. Bài vừa bị ẩn /
   * rút khỏi Knowledge Base trong lúc embed thì chỉ xoá chunk cũ, trả 0.
   */
  private int replaceChunks(Long articleId, List<String> chunks, List<float[]> vectors, String model) {
    knowledgeBaseChunkRepository.deleteByArticleAndModel(articleId, model);
    if (!expertArticleRepository.existsByIdAndStatusAndKbStatus(
        articleId, ExpertArticleStatus.PUBLISHED, ExpertArticleKbStatus.APPROVED)) {
      return 0;
    }
    for (int i = 0; i < chunks.size(); i++) {
      String content = chunks.get(i);
      knowledgeBaseChunkRepository.insertChunk(
          articleId,
          (short) i,
          content,
          KnowledgeBaseText.estimateTokens(content),
          model,
          KnowledgeBaseText.toVectorLiteral(vectors.get(i)));
    }
    return chunks.size();
  }

  private static BusinessException unavailable(String message) {
    return new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, message);
  }

  private record ArticleText(Long id, String content) {}
}
