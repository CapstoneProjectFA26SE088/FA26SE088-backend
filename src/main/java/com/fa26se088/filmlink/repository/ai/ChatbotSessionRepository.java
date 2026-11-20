package com.fa26se088.filmlink.repository.ai;

import com.fa26se088.filmlink.entity.ai.ChatbotSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotSessionRepository extends JpaRepository<ChatbotSession, Long> {

  /** UC19: session của đúng chủ; session của người khác coi như không tồn tại. */
  Optional<ChatbotSession> findByIdAndUserAccountId(Long id, Long userAccountId);
}
