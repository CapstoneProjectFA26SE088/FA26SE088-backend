package com.fa26se088.filmlink.service.ai.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * Hàm thuần cho Knowledge Base: chia bài thành chunk, ước lượng token, viết vector dạng chuỗi của
 * pgvector.
 */
final class KnowledgeBaseText {

  /** Độ dài tối đa một chunk, ký tự. */
  static final int MAX_CHUNK_CHARS = 2_000;

  /** Số chiều của {@code text-embedding-3-small}; query và index HNSW cố định 1536 chiều. */
  static final int EMBEDDING_DIMENSIONS = 1_536;

  private KnowledgeBaseText() {}

  /**
   * Chia nội dung theo đoạn (ngăn bởi dòng trống). Các đoạn liền nhau được gộp tới tối đa {@value
   * #MAX_CHUNK_CHARS} ký tự; đoạn dài hơn thì cắt ở khoảng trắng gần nhất trước giới hạn (không có
   * khoảng trắng thì cắt cứng).
   */
  static List<String> split(String content) {
    List<String> chunks = new ArrayList<>();
    if (content == null || content.isBlank()) {
      return chunks;
    }
    StringBuilder current = new StringBuilder();
    for (String raw : content.split("\\R\\s*\\R")) {
      String paragraph = raw.strip();
      if (paragraph.isEmpty()) {
        continue;
      }
      if (!current.isEmpty()
          && current.length() + 2 + paragraph.length() > MAX_CHUNK_CHARS) {
        chunks.add(current.toString());
        current.setLength(0);
      }
      if (paragraph.length() > MAX_CHUNK_CHARS) {
        chunks.addAll(splitLong(paragraph));
        continue;
      }
      if (!current.isEmpty()) {
        current.append("\n\n");
      }
      current.append(paragraph);
    }
    if (!current.isEmpty()) {
      chunks.add(current.toString());
    }
    return chunks;
  }

  /** {@code token_count} ước lượng: {@code ceil(ký tự / 4)}, tối thiểu 1. */
  static int estimateTokens(String text) {
    return Math.max(1, (text.length() + 3) / 4);
  }

  /** Vector dạng {@code [x1,x2,...]} để cast sang {@code public.vector} trong native query. */
  static String toVectorLiteral(float[] vector) {
    StringBuilder literal = new StringBuilder(vector.length * 12).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (!Float.isFinite(vector[i])) {
        throw new IllegalArgumentException("Vector has a non-finite value at " + i);
      }
      if (i > 0) {
        literal.append(',');
      }
      literal.append(vector[i]);
    }
    return literal.append(']').toString();
  }

  private static List<String> splitLong(String paragraph) {
    List<String> parts = new ArrayList<>();
    int start = 0;
    while (start < paragraph.length()) {
      int end = Math.min(start + MAX_CHUNK_CHARS, paragraph.length());
      if (end < paragraph.length()) {
        int space = lastWhitespace(paragraph, start, end);
        if (space > start) {
          end = space;
        } else if (Character.isHighSurrogate(paragraph.charAt(end - 1))) {
          end--; // không cắt đôi một ký tự ngoài BMP
        }
      }
      String part = paragraph.substring(start, end).strip();
      if (!part.isEmpty()) {
        parts.add(part);
      }
      start = end;
    }
    return parts;
  }

  private static int lastWhitespace(String text, int start, int end) {
    for (int i = end; i > start; i--) {
      if (Character.isWhitespace(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }
}
