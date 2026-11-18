package com.fa26se088.filmlink.repository.ai;

import com.fa26se088.filmlink.entity.ai.ChatbotMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, Long> {

  /**
   * UC19: message gần nhất trước (dùng {@code ix_chatbot_messages_session}); {@code id} phân xử khi
   * hai message cùng {@code created_at}. Service lấy N lượt gần nhất rồi đảo lại theo thời gian.
   */
  List<ChatbotMessage> findByChatbotSessionIdOrderByCreatedAtDescIdDesc(
      Long chatbotSessionId, Pageable pageable);

  /** Lịch sử session theo thời gian (cũ trước). */
  Page<ChatbotMessage> findByChatbotSessionIdOrderByCreatedAtAscIdAsc(
      Long chatbotSessionId, Pageable pageable);
}
