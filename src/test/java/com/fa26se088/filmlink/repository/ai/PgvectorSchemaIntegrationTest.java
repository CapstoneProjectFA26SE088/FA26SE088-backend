package com.fa26se088.filmlink.repository.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * M-05: migration pgvector đã chạy trên DB test (và CI có image pgvector). Kiểu, hàm, toán tử của
 * pgvector nằm ở schema {@code public}, ngoài search_path của app, nên query phải ghi {@code public.}.
 */
@SpringBootTest
@ActiveProfiles("test")
class PgvectorSchemaIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Extension vector đã được cài")
  void vectorExtension_isInstalled() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);

    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("Toán tử cosine distance của pgvector chạy được: hai vector vuông góc → 1.0")
  void cosineDistance_ofOrthogonalVectors_isOne() {
    Double distance =
        jdbcTemplate.queryForObject(
            "SELECT '[1,0,0]'::public.vector OPERATOR(public.<=>) '[0,1,0]'::public.vector",
            Double.class);

    assertThat(distance).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Bảng knowledge_base_chunks và cột image_analyses.embedding tồn tại trong schema app")
  void knowledgeBaseTableAndEmbeddingColumn_exist() {
    Integer tableCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables"
                + " WHERE table_schema = current_schema() AND table_name = 'knowledge_base_chunks'",
            Integer.class);
    String embeddingUdt =
        jdbcTemplate.queryForObject(
            "SELECT udt_name FROM information_schema.columns WHERE table_schema = current_schema()"
                + " AND table_name = 'image_analyses' AND column_name = 'embedding'",
            String.class);

    assertThat(tableCount).isEqualTo(1);
    assertThat(embeddingUdt).isEqualTo("vector");
  }
}
