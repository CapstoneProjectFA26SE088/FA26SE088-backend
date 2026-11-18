package com.fa26se088.filmlink.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** BR-29: chia Expert Article thành chunk ≤ 2000 ký tự, token ước lượng, vector dạng chuỗi. */
class KnowledgeBaseTextTest {

  @Test
  void split_mergesShortParagraphs_andKeepsChunksUnderLimit() {
    String paragraph = "a".repeat(900);
    String content = paragraph + "\n\n" + paragraph + "\r\n\r\n" + paragraph + "\n  \n" + "kết";

    List<String> chunks = KnowledgeBaseText.split(content);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).isEqualTo(paragraph + "\n\n" + paragraph);
    assertThat(chunks.get(1)).isEqualTo(paragraph + "\n\nkết");
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(2_000));
  }

  @Test
  void split_cutsLongParagraphAtWhitespace() {
    String longParagraph = ("từ ".repeat(1_500)).strip(); // ~4499 ký tự

    List<String> chunks = KnowledgeBaseText.split(longParagraph);

    assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(2_000));
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).doesNotStartWith(" ").doesNotEndWith(" "));
    assertThat(String.join(" ", chunks)).isEqualTo(longParagraph);
  }

  @Test
  void split_blankContent_returnsNoChunk() {
    assertThat(KnowledgeBaseText.split("  \n\n ")).isEmpty();
    assertThat(KnowledgeBaseText.split(null)).isEmpty();
  }

  @Test
  void estimateTokens_isCeilOfCharsOverFour() {
    assertThat(KnowledgeBaseText.estimateTokens("a")).isEqualTo(1);
    assertThat(KnowledgeBaseText.estimateTokens("abcd")).isEqualTo(1);
    assertThat(KnowledgeBaseText.estimateTokens("abcde")).isEqualTo(2);
    assertThat(KnowledgeBaseText.estimateTokens("a".repeat(2_000))).isEqualTo(500);
  }

  @Test
  void toVectorLiteral_formatsPgvectorText_andRejectsNaN() {
    assertThat(KnowledgeBaseText.toVectorLiteral(new float[] {1.0f, -0.5f, 0.0f}))
        .isEqualTo("[1.0,-0.5,0.0]");
    assertThatThrownBy(() -> KnowledgeBaseText.toVectorLiteral(new float[] {Float.NaN}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
