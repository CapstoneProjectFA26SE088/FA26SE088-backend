package com.fa26se088.filmlink.entity.ai.enums;

/**
 * Kết quả một lần gọi tính năng AI, khớp {@code ck_ai_interactions_status}.
 *
 * <ul>
 *   <li>{@code SUCCEEDED}: model trả lời từ ngữ cảnh.
 *   <li>{@code FAILED}: lỗi, không có câu trả lời dự phòng.
 *   <li>{@code FALLBACK}: LLM không khả dụng / lỗi, trả câu trả lời dự phòng (UH-08).
 *   <li>{@code REFUSED}: Knowledge Base không có thông tin, không trả lời (BR-29).
 * </ul>
 */
public enum AiInteractionStatus {
  SUCCEEDED,
  FAILED,
  FALLBACK,
  REFUSED
}
