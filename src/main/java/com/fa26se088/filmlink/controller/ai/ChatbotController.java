package com.fa26se088.filmlink.controller.ai;

import com.fa26se088.filmlink.dto.ai.request.AskChatbotQuestionRequest;
import com.fa26se088.filmlink.dto.ai.response.ChatbotAnswerResponse;
import com.fa26se088.filmlink.dto.ai.response.ChatbotMessageResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.ai.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Advisory Chatbot (UC19, F-23, BR-29). PHOTOGRAPHY_EXPERT kế thừa quyền PHOTOGRAPHER (BR-05). */
@RestController
@RequestMapping("/api/v1/chatbot")
@PreAuthorize("hasRole('PHOTOGRAPHER')")
@RequiredArgsConstructor
@Tag(name = "AI - Advisory Chatbot", description = "Chatbot tư vấn nhiếp ảnh phim (UC19, BR-29)")
public class ChatbotController {

  private final ChatbotService chatbotService;

  @PostMapping("/questions")
  @Operation(
      summary = "[Photographer] Hỏi chatbot (UC19)",
      description =
          "Chỉ trả lời từ các Expert Article đã duyệt vào Knowledge Base và trích dẫn bài (BR-29)."
              + " Không có sessionId thì tạo session mới. LLM không khả dụng: vẫn 200, fallback = true.")
  public ChatbotAnswerResponse ask(
      @Valid @RequestBody AskChatbotQuestionRequest request,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return chatbotService.ask(account.id(), request);
  }

  @GetMapping("/sessions/{sessionId}/messages")
  @Operation(
      summary = "[Photographer] Lịch sử một session chatbot (UC19)",
      description = "Chỉ chủ session; message cũ trước. Session của người khác trả 404.")
  public PageResponse<ChatbotMessageResponse> getMessages(
      @PathVariable Long sessionId,
      @PageableDefault(size = 20) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedAccount account) {
    return chatbotService.getMessages(account.id(), sessionId, pageable);
  }
}
