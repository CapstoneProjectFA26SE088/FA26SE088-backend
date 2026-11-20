package com.fa26se088.filmlink.service.account;

import com.fa26se088.filmlink.dto.account.request.SubmitLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nghiệp vụ nộp và tra cứu đơn đăng ký mở Film Lab (UC07, BR-16, UH-02).
 */
public interface LabApplicationService {

  /**
   * Guest nộp đơn đăng ký mở Film Lab mới kèm tài liệu pháp lý (UC07).
   *
   * @param request thông tin đơn đăng ký
   * @param documents danh sách 1..5 file tài liệu pháp lý đính kèm (PDF/JPG/PNG)
   * @return thông tin chi tiết đơn đăng ký đã được tạo
   */
  LabApplicationResponse submitApplication(
      SubmitLabApplicationRequest request, List<MultipartFile> documents);

  /**
   * Tra cứu đơn đăng ký theo mã application_code (MSG14).
   *
   * @param applicationCode mã đơn đăng ký (ví dụ LA261109ABC123)
   * @return thông tin chi tiết đơn đăng ký
   */
  LabApplicationResponse getApplicationByCode(String applicationCode);
}

