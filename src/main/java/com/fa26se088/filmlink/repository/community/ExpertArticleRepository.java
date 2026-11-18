package com.fa26se088.filmlink.repository.community;

import com.fa26se088.filmlink.entity.community.ExpertArticle;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleKbStatus;
import com.fa26se088.filmlink.entity.community.enums.ExpertArticleStatus;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Repository <b>chỉ đọc</b> của {@link ExpertArticle} (kế thừa {@link Repository}, không có {@code
 * save} / {@code delete}). Luồng viết / duyệt bài (UC42, UC44) sẽ mở rộng khi được giao.
 */
public interface ExpertArticleRepository extends Repository<ExpertArticle, Long> {

  /** BR-29: bài đủ điều kiện vào Knowledge Base, theo id tăng dần. */
  List<ExpertArticle> findByStatusAndKbStatusOrderByIdAsc(
      ExpertArticleStatus status, ExpertArticleKbStatus kbStatus);

  /** Bài còn đủ điều kiện vào Knowledge Base tại thời điểm ghi chunk. */
  boolean existsByIdAndStatusAndKbStatus(
      Long id, ExpertArticleStatus status, ExpertArticleKbStatus kbStatus);
}
