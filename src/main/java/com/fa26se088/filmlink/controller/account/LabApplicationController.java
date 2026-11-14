package com.fa26se088.filmlink.controller.account;

import com.fa26se088.filmlink.dto.account.request.SubmitLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.service.account.LabApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller công khai tiếp nhận và tra cứu đơn đăng ký mở Film Lab (UC07).
 */
@RestController
@RequestMapping("/api/v1/public/lab-applications")
@Tag(name = "Lab Application (Public)", description = "Đăng ký mở Film Lab cho Guest")
public class LabApplicationController {

  private final LabApplicationService labApplicationService;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final Validator validator =
      Validation.byDefaultProvider().configure().buildValidatorFactory().getValidator();

  public LabApplicationController(LabApplicationService labApplicationService) {
    this.labApplicationService = labApplicationService;
  }

  /**
   * Guest nộp đơn đăng ký mở Film Lab mới kèm 1..5 file tài liệu pháp lý (UC07).
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Guest nộp đơn đăng ký mở Film Lab (UC07)")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
              encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)))
  public LabApplicationResponse submitApplication(
      @RequestPart("data") String data,
      @RequestPart("documents") List<MultipartFile> documents) {
    SubmitLabApplicationRequest request;
    try {
      request = objectMapper.readValue(data, SubmitLabApplicationRequest.class);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dữ liệu JSON không hợp lệ: " + e.getMessage());
    }

    Set<ConstraintViolation<SubmitLabApplicationRequest>> violations = validator.validate(request);
    if (!violations.isEmpty()) {
      String firstError = violations.iterator().next().getMessage();
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, firstError);
    }

    return labApplicationService.submitApplication(request, documents);
  }

  /**
   * Tra cứu tiến độ xử lý của đơn đăng ký theo mã application_code (MSG14).
   */
  @GetMapping("/{applicationCode}")
  @Operation(summary = "Tra cứu đơn đăng ký theo mã tra cứu (MSG14)")
  public LabApplicationResponse getApplicationByCode(@PathVariable String applicationCode) {
    return labApplicationService.getApplicationByCode(applicationCode);
  }
}


