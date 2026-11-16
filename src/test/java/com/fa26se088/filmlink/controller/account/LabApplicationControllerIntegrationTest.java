package com.fa26se088.filmlink.controller.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.notification.EmailClient;
import com.fa26se088.filmlink.dto.account.request.SubmitLabApplicationRequest;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.ServiceType;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.repository.masterdata.ServiceTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabApplicationControllerIntegrationTest {

  private static final String API_PATH = "/api/v1/public/lab-applications";

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  @Autowired private LabRegistrationApplicationRepository applicationRepository;
  @Autowired private ServiceTypeRepository serviceTypeRepository;
  @Autowired private FilmProcessRepository filmProcessRepository;
  @Autowired private FilmFormatRepository filmFormatRepository;

  @MockitoBean private EmailClient emailClient;

  private Long activeServiceTypeId;
  private Long activeFilmProcessId;
  private Long activeFilmFormatId;

  @BeforeEach
  void setUp() {
    activeServiceTypeId = serviceTypeRepository.findAll().getFirst().getId();
    activeFilmProcessId = filmProcessRepository.findAll().getFirst().getId();
    activeFilmFormatId = filmFormatRepository.findAll().getFirst().getId();
  }

  @AfterEach
  void tearDown() {
    applicationRepository.deleteAll();
  }

  @Test
  @DisplayName("TC-F02-01 & TC-SM02-01: Guest nộp đơn đăng ký lab thành công -> 201 Created & PENDING_REVIEW")
  void submitApplication_validData_createsPendingReviewApplication() throws Exception {
    SubmitLabApplicationRequest request =
        new SubmitLabApplicationRequest(
            "Film Club Saigon",
            "0312345678",
            "456 Le Loi",
            "Quan 1",
            "TP Ho Chi Minh",
            "info@filmclub.vn",
            "0912345678",
            Set.of(activeServiceTypeId),
            Set.of(activeFilmProcessId),
            Set.of(activeFilmFormatId));

    MockMultipartFile dataPart =
        new MockMultipartFile(
            "data",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(request));

    MockMultipartFile doc1 =
        new MockMultipartFile(
            "documents",
            "business_license.pdf",
            "application/pdf",
            "%PDF-1.4 test document 1".getBytes(StandardCharsets.UTF_8));

    MockMultipartFile doc2 =
        new MockMultipartFile(
            "documents",
            "id_card.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});

    mockMvc
        .perform(
            multipart(API_PATH)
                .file(dataPart)
                .file(doc1)
                .file(doc2))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.applicationCode").value(matchesPattern("^LA[0-9]{6}[0-9A-Z]{6}$")))
        .andExpect(jsonPath("$.labName").value("Film Club Saigon"))
        .andExpect(jsonPath("$.taxCode").value("0312345678"))
        .andExpect(jsonPath("$.status").value(LabApplicationStatus.PENDING_REVIEW.name()))
        .andExpect(jsonPath("$.isDuplicateSuspected").value(false))
        .andExpect(jsonPath("$.documents.length()").value(2));

    verify(emailClient).send(eq("info@filmclub.vn"), anyString(), anyString());
  }

  @Test
  @DisplayName("Guest tra cứu đơn đăng ký theo application_code thành công")
  void getApplicationByCode_success() throws Exception {
    SubmitLabApplicationRequest request =
        new SubmitLabApplicationRequest(
            "Da Nang Film Lab",
            "0412345678",
            "789 Bach Dang",
            "Hai Chau",
            "Da Nang",
            "contact@danangfilmlab.com",
            "0987654321",
            Set.of(activeServiceTypeId),
            Set.of(activeFilmProcessId),
            Set.of(activeFilmFormatId));

    MockMultipartFile dataPart =
        new MockMultipartFile(
            "data",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(request));

    MockMultipartFile doc =
        new MockMultipartFile(
            "documents",
            "license.pdf",
            "application/pdf",
            "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8));

    String responseBody =
        mockMvc
            .perform(multipart(API_PATH).file(dataPart).file(doc))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    String appCode = objectMapper.readTree(responseBody).get("applicationCode").asText();

    mockMvc
        .perform(get(API_PATH + "/" + appCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationCode").value(appCode))
        .andExpect(jsonPath("$.labName").value("Da Nang Film Lab"))
        .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
  }

  @Test
  @DisplayName("Nộp đơn thiếu trường bắt buộc -> 400 Bad Request từ Validation")
  void submitApplication_missingFields_returns400() throws Exception {
    SubmitLabApplicationRequest invalidRequest =
        new SubmitLabApplicationRequest(
            "", // Tên trống
            "invalid-tax-code", // Mã số thuế sai regex
            "",
            "",
            "",
            "not-an-email",
            "",
            Set.of(),
            Set.of(),
            Set.of());

    MockMultipartFile dataPart =
        new MockMultipartFile(
            "data",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(invalidRequest));

    MockMultipartFile doc =
        new MockMultipartFile(
            "documents",
            "test.pdf",
            "application/pdf",
            new byte[] {1, 2});

    mockMvc
        .perform(multipart(API_PATH).file(dataPart).file(doc))
        .andExpect(status().isBadRequest());
  }
}
