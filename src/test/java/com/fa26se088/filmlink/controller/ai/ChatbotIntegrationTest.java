package com.fa26se088.filmlink.controller.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.llm.LlmClient;
import com.fa26se088.filmlink.config.CacheConfig;
import com.fa26se088.filmlink.client.llm.LlmClient.ChatMessage;
import com.fa26se088.filmlink.client.llm.LlmClient.ChatResult;
import com.fa26se088.filmlink.client.llm.LlmClient.ChatRole;
import com.fa26se088.filmlink.client.llm.LlmClient.EmbeddingResult;
import com.fa26se088.filmlink.client.llm.LlmClientException;
import com.fa26se088.filmlink.entity.account.UserAccount;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.account.enums.UserAccountStatus;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.repository.ai.KnowledgeBaseChunkRepository;
import com.fa26se088.filmlink.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * SPEC-01 T-12 (UC19, F-23, BR-29) trên DB thật (schema test, có pgvector): Admin lập chỉ mục
 * Knowledge Base, Photographer hỏi chatbot.
 *
 * <p>{@link LlmClient} là mock: không gọi Internet, không cần key. Embedding giả <b>xác định</b>:
 * vector 1536 chiều, mỗi từ khoá trong {@link #KEYWORDS} bật một chiều, nên bài "push Portra 400"
 * gần câu hỏi "push Portra 400 một stop" nhất.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatbotIntegrationTest {

  private static final String ASK_PATH = "/api/v1/chatbot/questions";
  private static final String REINDEX_PATH = "/api/v1/admin/knowledge-base/reindex";
  private static final String MODEL = KnowledgeBaseChunkRepository.SUPPORTED_EMBEDDING_MODEL;
  private static final String QUESTION = "Làm sao push Portra 400 một stop?";
  private static final String CHAT_ANSWER =
      "Để push Portra 400 thêm một stop, chụp ở ISO 800 và báo lab kéo dài thời gian tráng.";

  /** Mỗi từ khoá bật một chiều của vector giả. */
  private static final List<String> KEYWORDS =
      List.of("push", "portra", "400", "stop", "hp5", "pull", "nắng", "bảo quản", "tủ lạnh",
          "scan", "độ phân giải");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private LlmClient llmClient;

  private final List<Long> accountIds = new ArrayList<>();
  private UserAccount admin;
  private UserAccount photographer;
  private UserAccount author;

  @BeforeEach
  void setUp() {
    admin = createAccount(UserAccountRole.ADMIN);
    photographer = createAccount(UserAccountRole.PHOTOGRAPHER);
    author = createAccount(UserAccountRole.PHOTOGRAPHY_EXPERT);

    when(llmClient.isAvailable()).thenReturn(true);
    when(llmClient.embed(anyList(), anyString()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return new EmbeddingResult(
                  texts.stream().map(ChatbotIntegrationTest::fakeEmbedding).toList(),
                  invocation.getArgument(1),
                  texts.size() * 10);
            });
    when(llmClient.chat(anyList()))
        .thenReturn(new ChatResult(CHAT_ANSWER, "gpt-4o-mini", 300, 40));
  }

  @AfterEach
  void cleanUp() {
    if (accountIds.isEmpty()) {
      return;
    }
    String ids = accountIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow();
    // chatbot_messages: ON DELETE CASCADE theo chatbot_sessions; xoá trước ai_interactions (FK).
    jdbcTemplate.update("DELETE FROM chatbot_sessions WHERE user_account_id IN (" + ids + ")");
    jdbcTemplate.update("DELETE FROM ai_interactions WHERE user_account_id IN (" + ids + ")");
    // knowledge_base_chunks: ON DELETE CASCADE theo expert_articles.
    jdbcTemplate.update("DELETE FROM expert_articles WHERE author_account_id IN (" + ids + ")");
    for (Long id : accountIds) {
      jdbcTemplate.update(
          "DELETE FROM user_accounts WHERE id = ?"
              + " AND NOT EXISTS (SELECT 1 FROM audit_logs WHERE actor_account_id = ?)",
          id,
          id);
    }
    accountIds.clear();
  }

  // --- Must ---

  /**
   * TC-F23-01 (UC19, BR-29): 3 bài đã duyệt trong Knowledge Base (+1 bài PENDING_REVIEW); hỏi
   * "Làm sao push Portra 400 một stop?" → 200, trích dẫn bài push Portra đầu tiên, không trích bài
   * chưa duyệt, có 1 dòng {@code ai_interactions} ADVISORY_CHATBOT SUCCEEDED và 2 dòng {@code
   * chatbot_messages}.
   */
  @Test
  @DisplayName("TC-F23-01 UC19 ask push Portra 400 -> cites approved article, interaction logged")
  void tcF2301_answerCitesApprovedArticle() throws Exception {
    long portra = insertApprovedPortraArticle();
    insertArticle("Pull HP5 Plus khi trời nắng gắt", hp5Content(), "PUBLISHED", "APPROVED");
    insertArticle("Bảo quản phim trong tủ lạnh", storageContent(), "PUBLISHED", "APPROVED");
    long pending = insertPendingPortraArticle();

    reindex(admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.articleCount").value(3))
        .andExpect(jsonPath("$.chunkCount").value(3))
        .andExpect(jsonPath("$.embeddingModel").value(MODEL));
    assertThat(chunkCount(pending)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'KNOWLEDGE_BASE_REINDEXED'"
                    + " AND actor_account_id = ? AND after_data ->> 'chunkCount' = '3'",
                Integer.class,
                admin.getId()))
        .isEqualTo(1);

    String body =
        ask(photographer, QUESTION, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value(CHAT_ANSWER))
            .andExpect(jsonPath("$.fallback").value(false))
            .andExpect(jsonPath("$.citations", not(empty())))
            .andExpect(jsonPath("$.citations[0].articleId").value(portra))
            .andExpect(jsonPath("$.citations[0].title").value("Push Portra 400 thêm một stop"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<Integer> citedIds = JsonPath.read(body, "$.citations[*].articleId");
    assertThat(citedIds).doesNotContain((int) pending).hasSizeLessThanOrEqualTo(3);
    long sessionId = ((Number) JsonPath.read(body, "$.sessionId")).longValue();

    Map<String, Object> interaction = onlyInteraction(photographer);
    assertThat(interaction.get("feature")).isEqualTo("ADVISORY_CHATBOT");
    assertThat(interaction.get("status")).isEqualTo("SUCCEEDED");
    assertThat(interaction.get("is_fallback")).isEqualTo(false);
    assertThat(interaction.get("model_name")).isEqualTo("gpt-4o-mini");
    assertThat(interaction.get("latency_ms")).isNotNull();
    assertThat(interaction.get("question")).isEqualTo(QUESTION);
    assertThat(interaction.get("error_message")).isNull();

    List<Map<String, Object>> messages = messagesOf(sessionId);
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).get("sender")).isEqualTo("USER");
    assertThat(messages.get(0).get("content")).isEqualTo(QUESTION);
    assertThat(messages.get(1).get("sender")).isEqualTo("ASSISTANT");
    assertThat(messages.get(1).get("first_cited")).isEqualTo(String.valueOf(portra));
    assertThat(messages.get(1).get("ai_interaction_id")).isEqualTo(interaction.get("id"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT title FROM chatbot_sessions WHERE id = ?", String.class, sessionId))
        .isEqualTo(QUESTION);

    // Ngữ cảnh gửi model chỉ gồm chunk của bài đã duyệt, bài Portra đứng đầu.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(llmClient).chat(captor.capture());
    List<ChatMessage> sent = captor.getValue();
    assertThat(sent.getFirst().role()).isEqualTo(ChatRole.SYSTEM);
    assertThat(sent.getFirst().content())
        .contains("[1] Bài: \"Push Portra 400 thêm một stop\"")
        .doesNotContain("chưa duyệt");
    assertThat(sent.getLast()).isEqualTo(new ChatMessage(ChatRole.USER, QUESTION));
  }

  // --- Dự phòng / từ chối ---

  /** UH-08: LLM chưa cấu hình → 200, câu dự phòng, FALLBACK, không gọi embedding / chat. */
  @Test
  void clientUnavailable_returnsFallbackWith200() throws Exception {
    when(llmClient.isAvailable()).thenReturn(false);

    String body =
        ask(photographer, QUESTION, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fallback").value(true))
            .andExpect(jsonPath("$.citations", empty()))
            .andExpect(jsonPath("$.answer").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    Map<String, Object> interaction = onlyInteraction(photographer);
    assertThat(interaction.get("status")).isEqualTo("FALLBACK");
    assertThat(interaction.get("is_fallback")).isEqualTo(true);
    assertThat(messagesOf(((Number) JsonPath.read(body, "$.sessionId")).longValue())).hasSize(2);
    verify(llmClient, never()).embed(anyList(), anyString());
    verify(llmClient, never()).chat(anyList());
  }

  /**
   * UH-08: chat lỗi (ví dụ timeout, HTTP 500) → 200, câu dự phòng, FALLBACK kèm error_message.
   * Trùng nội dung với TC-F23-03 (Later) nên không gắn mã TC.
   */
  @Test
  void chatFails_returnsFallbackWith200() throws Exception {
    insertApprovedPortraArticle();
    reindex(admin).andExpect(status().isOk());
    when(llmClient.chat(anyList()))
        .thenThrow(new LlmClientException("LLM call /chat/completions returned HTTP 500"));

    ask(photographer, QUESTION, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallback").value(true))
        .andExpect(jsonPath("$.citations", empty()));

    Map<String, Object> interaction = onlyInteraction(photographer);
    assertThat(interaction.get("status")).isEqualTo("FALLBACK");
    assertThat(interaction.get("is_fallback")).isEqualTo(true);
    assertThat((String) interaction.get("error_message")).contains("HTTP 500");
  }

  /** BR-29: Knowledge Base trống → REFUSED, không trích dẫn, không gọi chat. */
  @Test
  void emptyKnowledgeBase_refusesWithoutCallingChat() throws Exception {
    ask(photographer, QUESTION, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallback").value(false))
        .andExpect(jsonPath("$.citations", empty()))
        .andExpect(jsonPath("$.answer").value(containsString("kho kiến thức")));

    Map<String, Object> interaction = onlyInteraction(photographer);
    assertThat(interaction.get("status")).isEqualTo("REFUSED");
    assertThat(interaction.get("is_fallback")).isEqualTo(false);
    verify(llmClient, times(1)).embed(anyList(), anyString());
    verify(llmClient, never()).chat(anyList());
  }

  /** BR-29: model báo ngữ cảnh không đủ → REFUSED, không trích dẫn. */
  @Test
  void modelSaysContextInsufficient_refusesWithoutCitations() throws Exception {
    insertApprovedPortraArticle();
    reindex(admin).andExpect(status().isOk());
    when(llmClient.chat(anyList()))
        .thenReturn(new ChatResult("[KHONG_CO_THONG_TIN]", "gpt-4o-mini", 200, 5));

    ask(photographer, "Máy Leica M6 dùng pin gì?", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fallback").value(false))
        .andExpect(jsonPath("$.citations", empty()));

    assertThat(onlyInteraction(photographer).get("status")).isEqualTo("REFUSED");
  }

  /** BR-29: chunk của bài chưa duyệt (dù gần câu hỏi nhất) không bao giờ được lấy. */
  @Test
  void chunkOfUnapprovedArticle_isNeverRetrieved() throws Exception {
    insertApprovedPortraArticle();
    reindex(admin).andExpect(status().isOk());
    long pending = insertPendingPortraArticle();
    jdbcTemplate.update(
        "INSERT INTO knowledge_base_chunks"
            + " (expert_article_id, chunk_index, content, token_count, embedding_model, embedding)"
            + " VALUES (?, 0, 'push portra 400 stop', 5, ?, CAST(? AS public.vector))",
        pending,
        MODEL,
        vectorLiteral(fakeEmbedding(QUESTION)));

    String body =
        ask(photographer, QUESTION, null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    List<Integer> citedIds = JsonPath.read(body, "$.citations[*].articleId");
    assertThat(citedIds).isNotEmpty().doesNotContain((int) pending);
    Long pendingChunkId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM knowledge_base_chunks WHERE expert_article_id = ?", Long.class, pending);
    String retrieved =
        jdbcTemplate.queryForObject(
            "SELECT request_payload ->> 'retrievedChunkIds' FROM ai_interactions"
                + " WHERE user_account_id = ?",
            String.class,
            photographer.getId());
    assertThat(retrieved).doesNotContain(String.valueOf(pendingChunkId));
  }

  /** UC19: số trích dẫn không vượt CHATBOT_MAX_CITATIONS dù ngữ cảnh có nhiều bài hơn. */
  @Test
  void citations_areCappedByMaxCitations() throws Exception {
    insertApprovedPortraArticle();
    insertArticle("Pull HP5 Plus khi trời nắng gắt", hp5Content(), "PUBLISHED", "APPROVED");
    insertArticle("Bảo quản phim trong tủ lạnh", storageContent(), "PUBLISHED", "APPROVED");
    insertArticle("Chọn độ phân giải scan phù hợp", scanContent(), "PUBLISHED", "APPROVED");
    reindex(admin).andExpect(status().isOk()).andExpect(jsonPath("$.articleCount").value(4));
    int maxCitations =
        Integer.parseInt(
            jdbcTemplate.queryForObject(
                "SELECT param_value FROM system_parameters WHERE param_key = 'CHATBOT_MAX_CITATIONS'",
                String.class));

    ask(photographer, QUESTION, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations", hasSize(maxCitations)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(llmClient).chat(captor.capture());
    assertThat(captor.getValue().getFirst().content()).contains("[4] Bài:");
  }

  // --- Session ---

  /** UC19: hỏi tiếp cùng sessionId → lượt trước được gửi làm ngữ cảnh hội thoại; lịch sử đọc lại được. */
  @Test
  void followUpInSameSession_sendsPreviousTurn() throws Exception {
    insertApprovedPortraArticle();
    reindex(admin).andExpect(status().isOk());
    long sessionId = sessionIdOf(ask(photographer, QUESTION, null).andExpect(status().isOk()));
    String followUp = "Vậy push hai stop thì sao?";

    ask(photographer, followUp, sessionId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(sessionId));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(llmClient, times(2)).chat(captor.capture());
    List<ChatMessage> second = captor.getAllValues().get(1);
    assertThat(second).hasSize(4);
    assertThat(second.get(1)).isEqualTo(new ChatMessage(ChatRole.USER, QUESTION));
    assertThat(second.get(2)).isEqualTo(new ChatMessage(ChatRole.ASSISTANT, CHAT_ANSWER));
    assertThat(second.get(3)).isEqualTo(new ChatMessage(ChatRole.USER, followUp));
    assertThat(messagesOf(sessionId)).hasSize(4);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chatbot_sessions WHERE user_account_id = ?",
                Integer.class,
                photographer.getId()))
        .isEqualTo(1);

    mockMvc
        .perform(
            get("/api/v1/chatbot/sessions/{id}/messages", sessionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(photographer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[0].sender").value("USER"))
        .andExpect(jsonPath("$.content[0].content").value(QUESTION))
        .andExpect(jsonPath("$.content[1].sender").value("ASSISTANT"))
        .andExpect(jsonPath("$.content[1].citations[0].articleId").isNumber())
        .andExpect(jsonPath("$.content[3].content").value(CHAT_ANSWER));
  }

  /** UC19: session của người khác → 404 khi hỏi tiếp và khi xem lịch sử; không ghi gì. */
  @Test
  void otherUsersSession_returns404() throws Exception {
    when(llmClient.isAvailable()).thenReturn(false);
    long sessionId = sessionIdOf(ask(photographer, QUESTION, null).andExpect(status().isOk()));
    UserAccount other = createAccount(UserAccountRole.PHOTOGRAPHER);

    ask(other, QUESTION, sessionId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("AI_CHAT_SESSION_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/v1/chatbot/sessions/{id}/messages", sessionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("AI_CHAT_SESSION_NOT_FOUND"));
    assertThat(messagesOf(sessionId)).hasSize(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_interactions WHERE user_account_id = ?",
                Integer.class,
                other.getId()))
        .isZero();
  }

  // --- Validation / phân quyền ---

  @Test
  void questionShorterThan5_returns400() throws Exception {
    ask(photographer, "abc", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    ask(photographer, "   ab   ", null).andExpect(status().isBadRequest());
    verify(llmClient, never()).embed(anyList(), anyString());
  }

  @Test
  void unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post(ASK_PATH).contentType(MediaType.APPLICATION_JSON).content(askBody(QUESTION, null)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void filmLabOwnerAsks_returns403() throws Exception {
    ask(createAccount(UserAccountRole.FILM_LAB_OWNER), QUESTION, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
  }

  /** BR-05: PHOTOGRAPHY_EXPERT kế thừa quyền PHOTOGRAPHER. */
  @Test
  void photographyExpertCanAsk() throws Exception {
    when(llmClient.isAvailable()).thenReturn(false);

    ask(author, QUESTION, null).andExpect(status().isOk());
  }

  // --- EMBEDDING_MODEL không khớp hằng của query / index ---

  /** EMBEDDING_MODEL khác model query hỗ trợ → chatbot trả dự phòng, không gọi embedding / chat. */
  @Test
  void unsupportedEmbeddingModel_chatbotFallsBack() throws Exception {
    insertApprovedPortraArticle();
    reindex(admin).andExpect(status().isOk());

    withEmbeddingModel(
        "text-embedding-3-large",
        () ->
            ask(photographer, QUESTION, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.citations", empty())));

    Map<String, Object> interaction = onlyInteraction(photographer);
    assertThat(interaction.get("status")).isEqualTo("FALLBACK");
    assertThat((String) interaction.get("error_message")).contains("EMBEDDING_MODEL");
    verify(llmClient, times(1)).embed(anyList(), anyString()); // chỉ lần reindex
    verify(llmClient, never()).chat(anyList());
  }

  /** EMBEDDING_MODEL khác model query hỗ trợ → reindex 503, không ghi chunk. */
  @Test
  void unsupportedEmbeddingModel_reindexReturns503() throws Exception {
    long portra = insertApprovedPortraArticle();

    withEmbeddingModel(
        "text-embedding-3-large",
        () ->
            reindex(admin)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AI_PROVIDER_UNAVAILABLE")));

    assertThat(chunkCount(portra)).isZero();
    verify(llmClient, never()).embed(anyList(), anyString());
  }

  // --- Reindex ---

  @Test
  void reindex_clientUnavailable_returns503() throws Exception {
    when(llmClient.isAvailable()).thenReturn(false);
    long portra = insertApprovedPortraArticle();

    reindex(admin)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("AI_PROVIDER_UNAVAILABLE"));
    assertThat(chunkCount(portra)).isZero();
  }

  @Test
  void reindex_embeddingFails_returns503() throws Exception {
    long portra = insertApprovedPortraArticle();
    when(llmClient.embed(anyList(), anyString()))
        .thenThrow(new LlmClientException("LLM call /embeddings timed out"));

    reindex(admin)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("AI_PROVIDER_UNAVAILABLE"));
    assertThat(chunkCount(portra)).isZero();
  }

  @Test
  void reindex_byNonAdmin_returns403() throws Exception {
    reindex(photographer).andExpect(status().isForbidden());
    reindex(createAccount(UserAccountRole.MODERATOR)).andExpect(status().isForbidden());
    verify(llmClient, never()).embed(anyList(), anyString());
  }

  /** Reindex lần hai thay chunk cũ của bài, không nhân đôi. */
  @Test
  void reindexTwice_replacesChunks() throws Exception {
    long portra = insertApprovedPortraArticle();

    reindex(admin).andExpect(status().isOk());
    reindex(admin).andExpect(status().isOk()).andExpect(jsonPath("$.chunkCount").value(1));

    assertThat(chunkCount(portra)).isEqualTo(1);
    Map<String, Object> chunk =
        jdbcTemplate.queryForMap(
            "SELECT chunk_index, token_count, embedding_model,"
                + " public.vector_dims(embedding) AS dims FROM knowledge_base_chunks"
                + " WHERE expert_article_id = ?",
            portra);
    assertThat(((Number) chunk.get("chunk_index")).intValue()).isZero();
    assertThat(((Number) chunk.get("token_count")).intValue())
        .isEqualTo((portraContent().length() + 3) / 4);
    assertThat(chunk.get("embedding_model")).isEqualTo(MODEL);
    assertThat(((Number) chunk.get("dims")).intValue()).isEqualTo(1536);
  }

  // ---------------------------------------------------------------- helpers

  private ResultActions ask(UserAccount account, String question, Long sessionId) throws Exception {
    return mockMvc.perform(
        post(ASK_PATH)
            .header(HttpHeaders.AUTHORIZATION, bearer(account))
            .contentType(MediaType.APPLICATION_JSON)
            .content(askBody(question, sessionId)));
  }

  /**
   * Tạm đổi EMBEDDING_MODEL trong DB (bỏ qua màn Admin, vốn chặn sửa key này), chạy {@code action},
   * rồi trả lại giá trị cũ. Xoá cache tham số trước và sau.
   */
  private void withEmbeddingModel(String model, ThrowingRunnable action) throws Exception {
    String original =
        jdbcTemplate.queryForObject(
            "SELECT param_value FROM system_parameters WHERE param_key = 'EMBEDDING_MODEL'",
            String.class);
    jdbcTemplate.update(
        "UPDATE system_parameters SET param_value = ? WHERE param_key = 'EMBEDDING_MODEL'", model);
    clearParameterCache();
    try {
      action.run();
    } finally {
      jdbcTemplate.update(
          "UPDATE system_parameters SET param_value = ? WHERE param_key = 'EMBEDDING_MODEL'",
          original);
      clearParameterCache();
    }
  }

  private void clearParameterCache() {
    var cache = cacheManager.getCache(CacheConfig.CACHE_SYSTEM_PARAMETERS);
    if (cache != null) {
      cache.clear();
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private ResultActions reindex(UserAccount account) throws Exception {
    return mockMvc.perform(post(REINDEX_PATH).header(HttpHeaders.AUTHORIZATION, bearer(account)));
  }

  private static String askBody(String question, Long sessionId) {
    return "{\"question\":\"" + question + "\",\"sessionId\":" + sessionId + "}";
  }

  private static long sessionIdOf(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
  }

  private Map<String, Object> onlyInteraction(UserAccount account) {
    return jdbcTemplate.queryForMap(
        "SELECT id, feature, status, is_fallback, model_name, latency_ms, error_message,"
            + " request_payload ->> 'question' AS question"
            + " FROM ai_interactions WHERE user_account_id = ?",
        account.getId());
  }

  private List<Map<String, Object>> messagesOf(long sessionId) {
    return jdbcTemplate.queryForList(
        "SELECT sender, content, ai_interaction_id, citations -> 0 ->> 'expertArticleId' AS first_cited"
            + " FROM chatbot_messages WHERE chatbot_session_id = ? ORDER BY created_at, id",
        sessionId);
  }

  private int chunkCount(long articleId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM knowledge_base_chunks WHERE expert_article_id = ?",
        Integer.class,
        articleId);
  }

  private long insertApprovedPortraArticle() {
    return insertArticle("Push Portra 400 thêm một stop", portraContent(), "PUBLISHED", "APPROVED");
  }

  private long insertPendingPortraArticle() {
    return insertArticle(
        "Push Portra 400 hai stop (chưa duyệt)",
        "Bài chưa duyệt về push Portra 400 hai stop. " + portraContent(),
        "PUBLISHED",
        "PENDING_REVIEW");
  }

  /** Bài Expert Article; APPROVED có ngày gửi + ngày duyệt, PENDING_REVIEW chỉ có ngày gửi. */
  private long insertArticle(String title, String content, String status, String kbStatus) {
    Instant now = Instant.now();
    boolean reviewed = "APPROVED".equals(kbStatus);
    return jdbcTemplate.queryForObject(
        "INSERT INTO expert_articles (author_account_id, title, category, content, status,"
            + " kb_status, kb_submitted_at, kb_reviewed_at)"
            + " VALUES (?, ?, 'TUTORIAL', ?, ?, ?, ?, ?) RETURNING id",
        Long.class,
        author.getId(),
        title,
        content,
        status,
        kbStatus,
        Timestamp.from(now),
        reviewed ? Timestamp.from(now) : null);
  }

  private static String portraContent() {
    return "Khi push Portra 400 thêm một stop, bạn chụp như ISO 800 và báo lab kéo dài thời gian"
        + " tráng C-41 khoảng 30%. Push một stop làm tăng tương phản và hạt nhưng màu da vẫn đẹp.";
  }

  private static String hp5Content() {
    return "HP5 Plus có thể pull xuống ISO 200 khi trời nắng gắt. Lab rút ngắn thời gian tráng để"
        + " giảm tương phản, vùng sáng giữ được chi tiết, phù hợp chụp biển giữa trưa.";
  }

  private static String storageContent() {
    return "Bảo quản phim chưa chụp trong tủ lạnh ở 2-8 độ giúp màu ổn định lâu hơn. Trước khi"
        + " chụp, để cuộn phim về nhiệt độ phòng khoảng hai giờ để tránh đọng hơi nước.";
  }

  private static String scanContent() {
    return "Độ phân giải scan quyết định khổ in tối đa. Scan 3000 x 2000 điểm ảnh đủ cho ảnh đăng"
        + " mạng xã hội; muốn in khổ lớn nên chọn mức scan cao nhất của lab.";
  }

  /** Vector 1536 chiều xác định: mỗi từ khoá có trong văn bản bật một chiều, chiều cuối là nền. */
  static float[] fakeEmbedding(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    float[] vector = new float[1536];
    for (int i = 0; i < KEYWORDS.size(); i++) {
      if (lower.contains(KEYWORDS.get(i))) {
        vector[i] = 1f;
      }
    }
    vector[1535] = 0.05f;
    return vector;
  }

  private static String vectorLiteral(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(i == 0 ? "" : ",").append(vector[i]);
    }
    return sb.append(']').toString();
  }

  private UserAccount createAccount(UserAccountRole role) {
    UserAccount account = new UserAccount();
    account.setEmail("ai-it-" + UUID.randomUUID() + "@example.com");
    account.setPasswordHash("not-a-real-hash");
    account.setFullName("Chatbot Tester");
    account.setRole(role);
    account.setStatus(UserAccountStatus.ACTIVE);
    account.setTermsAcceptedAt(Instant.now());
    userAccountRepository.save(account);
    accountIds.add(account.getId());
    return account;
  }

  private String bearer(UserAccount account) {
    return "Bearer "
        + jwtTokenProvider.generateAccessToken(account.getId(), account.getEmail(), account.getRole());
  }
}
