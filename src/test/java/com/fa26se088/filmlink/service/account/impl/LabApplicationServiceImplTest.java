package com.fa26se088.filmlink.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.notification.EmailClient;
import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.account.request.SubmitLabApplicationRequest;
import com.fa26se088.filmlink.dto.account.response.LabApplicationResponse;
import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.ServiceType;
import com.fa26se088.filmlink.entity.masterdata.enums.CatalogStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.account.LabApplicationMapper;
import com.fa26se088.filmlink.repository.account.LabRegistrationApplicationRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmFormatRepository;
import com.fa26se088.filmlink.repository.masterdata.FilmProcessRepository;
import com.fa26se088.filmlink.repository.masterdata.ServiceTypeRepository;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class LabApplicationServiceImplTest {

  @Mock private LabRegistrationApplicationRepository applicationRepository;
  @Mock private ServiceTypeRepository serviceTypeRepository;
  @Mock private FilmProcessRepository filmProcessRepository;
  @Mock private FilmFormatRepository filmFormatRepository;
  @Mock private StorageClient storageClient;
  @Mock private EmailClient emailClient;

  private LabApplicationServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new LabApplicationServiceImpl(
            applicationRepository,
            serviceTypeRepository,
            filmProcessRepository,
            filmFormatRepository,
            storageClient,
            emailClient,
            new LabApplicationMapper());
  }

  @Test
  @DisplayName("TC-F02-01: Guest nộp đơn hợp lệ thành công -> tạo bản ghi PENDING_REVIEW, gửi email")
  void submitApplication_success() {
    SubmitLabApplicationRequest request = createValidRequest();
    MockMultipartFile file1 =
        new MockMultipartFile("documents", "doc1.pdf", "application/pdf", new byte[] {1, 2, 3});
    MockMultipartFile file2 =
        new MockMultipartFile("documents", "doc2.pdf", "application/pdf", new byte[] {4, 5, 6});

    mockActiveMasterData();
    when(applicationRepository.existsByTaxCode(anyString())).thenReturn(false);
    when(applicationRepository
            .existsByAddressLineIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(
                anyString(), anyString(), anyString()))
        .thenReturn(false);
    when(applicationRepository.existsByApplicationCode(anyString())).thenReturn(false);
    when(storageClient.upload(eq(StoragePurpose.LAB_DOCUMENT), anyString(), any(InputStream.class), eq(3L)))
        .thenReturn("lab-document/2026/11/doc1.pdf", "lab-document/2026/11/doc2.pdf");
    when(storageClient.signedUrl(anyString())).thenReturn(URI.create("https://storage.local/test.pdf"));
    when(applicationRepository.save(any(LabRegistrationApplication.class)))
        .thenAnswer(inv -> {
          LabRegistrationApplication app = inv.getArgument(0);
          ReflectionTestUtils.setField(app, "id", 100L);
          return app;
        });

    LabApplicationResponse response = service.submitApplication(request, List.of(file1, file2));

    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(100L);
    assertThat(response.status()).isEqualTo(LabApplicationStatus.PENDING_REVIEW);
    assertThat(response.applicationCode()).startsWith("LA");
    assertThat(response.isDuplicateSuspected()).isFalse();
    assertThat(response.documents()).hasSize(2);

    ArgumentCaptor<LabRegistrationApplication> appCaptor =
        ArgumentCaptor.forClass(LabRegistrationApplication.class);
    verify(applicationRepository).save(appCaptor.capture());
    LabRegistrationApplication saved = appCaptor.getValue();
    assertThat(saved.getStatus()).isEqualTo(LabApplicationStatus.PENDING_REVIEW);
    assertThat(saved.getLabName()).isEqualTo("Saigon Darkroom");

    verify(emailClient).send(eq("contact@saigondarkroom.vn"), anyString(), anyString());
  }

  @Test
  @DisplayName("UH-02: Trùng mã số thuế -> đánh dấu is_duplicate_suspected = true nhưng vẫn lưu đơn")
  void submitApplication_duplicateTaxCode_setsDuplicateSuspected() {
    SubmitLabApplicationRequest request = createValidRequest();
    MockMultipartFile file =
        new MockMultipartFile("documents", "doc.pdf", "application/pdf", new byte[] {1, 2});

    mockActiveMasterData();
    when(applicationRepository.existsByTaxCode("0123456789")).thenReturn(true);
    when(storageClient.upload(any(), any(), any(), eq(2L))).thenReturn("lab-doc/1.pdf");
    when(applicationRepository.save(any(LabRegistrationApplication.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    LabApplicationResponse response = service.submitApplication(request, List.of(file));

    assertThat(response.isDuplicateSuspected()).isTrue();
  }

  @Test
  @DisplayName("Hồ sơ không có tài liệu nào -> ném ngoại lệ LAB_APPLICATION_DOCUMENT_COUNT_INVALID")
  void submitApplication_emptyDocuments_throwsException() {
    SubmitLabApplicationRequest request = createValidRequest();

    assertThatThrownBy(() -> service.submitApplication(request, Collections.emptyList()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode())
                .isEqualTo(ErrorCode.LAB_APPLICATION_DOCUMENT_COUNT_INVALID));

    verify(applicationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Hồ sơ gửi quá 5 tài liệu -> ném ngoại lệ LAB_APPLICATION_DOCUMENT_COUNT_INVALID")
  void submitApplication_moreThanFiveDocuments_throwsException() {
    SubmitLabApplicationRequest request = createValidRequest();
    List<MultipartFile> sixFiles =
        List.of(
            new MockMultipartFile("documents", "1.pdf", "application/pdf", new byte[] {1}),
            new MockMultipartFile("documents", "2.pdf", "application/pdf", new byte[] {1}),
            new MockMultipartFile("documents", "3.pdf", "application/pdf", new byte[] {1}),
            new MockMultipartFile("documents", "4.pdf", "application/pdf", new byte[] {1}),
            new MockMultipartFile("documents", "5.pdf", "application/pdf", new byte[] {1}),
            new MockMultipartFile("documents", "6.pdf", "application/pdf", new byte[] {1}));

    assertThatThrownBy(() -> service.submitApplication(request, sixFiles))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode())
                .isEqualTo(ErrorCode.LAB_APPLICATION_DOCUMENT_COUNT_INVALID));
  }

  @Test
  @DisplayName("BR-16: Chọn dịch vụ không ACTIVE -> ném ngoại lệ LAB_APPLICATION_CATALOG_NOT_ACTIVE")
  void submitApplication_inactiveServiceType_throwsException() {
    SubmitLabApplicationRequest request = createValidRequest();
    MockMultipartFile file =
        new MockMultipartFile("documents", "1.pdf", "application/pdf", new byte[] {1});

    ServiceType inactiveService = new ServiceType();
    ReflectionTestUtils.setField(inactiveService, "id", 1L);
    inactiveService.setStatus(CatalogStatus.DISCONTINUED);
    when(serviceTypeRepository.findAllById(request.serviceTypeIds()))
        .thenReturn(List.of(inactiveService));

    assertThatThrownBy(() -> service.submitApplication(request, List.of(file)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode())
                .isEqualTo(ErrorCode.LAB_APPLICATION_CATALOG_NOT_ACTIVE));

    verify(storageClient, never()).upload(any(), any(), any(), any(Long.class));
  }

  @Test
  @DisplayName("Atomic Operation: Upload file thứ 2 thất bại -> xóa blob file thứ 1 đã upload")
  void submitApplication_uploadFailure_cleansUpUploadedBlobs() {
    SubmitLabApplicationRequest request = createValidRequest();
    MockMultipartFile file1 =
        new MockMultipartFile("documents", "doc1.pdf", "application/pdf", new byte[] {1});
    MockMultipartFile file2 =
        new MockMultipartFile("documents", "doc2.pdf", "application/pdf", new byte[] {2});

    mockActiveMasterData();
    when(storageClient.upload(eq(StoragePurpose.LAB_DOCUMENT), eq("doc1.pdf"), any(), eq(1L)))
        .thenReturn("lab-document/key1");
    when(storageClient.upload(eq(StoragePurpose.LAB_DOCUMENT), eq("doc2.pdf"), any(), eq(1L)))
        .thenThrow(new BusinessException(ErrorCode.STORAGE_FILE_TOO_LARGE, "File quá lớn"));

    assertThatThrownBy(() -> service.submitApplication(request, List.of(file1, file2)))
        .isInstanceOf(BusinessException.class);

    verify(storageClient).delete("lab-document/key1");
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Tra cứu đơn đăng ký theo mã thành công")
  void getApplicationByCode_success() {
    LabRegistrationApplication app = new LabRegistrationApplication();
    ReflectionTestUtils.setField(app, "id", 5L);
    app.setApplicationCode("LA261109TEST01");
    app.setLabName("Hanoi Film Lab");
    app.setTaxCode("0987654321");
    app.setStatus(LabApplicationStatus.PENDING_REVIEW);

    when(applicationRepository.findByApplicationCode("LA261109TEST01")).thenReturn(Optional.of(app));

    LabApplicationResponse response = service.getApplicationByCode("LA261109TEST01");

    assertThat(response.applicationCode()).isEqualTo("LA261109TEST01");
    assertThat(response.labName()).isEqualTo("Hanoi Film Lab");
  }

  @Test
  @DisplayName("Tra cứu đơn không tồn tại -> ném ngoại lệ LAB_APPLICATION_NOT_FOUND")
  void getApplicationByCode_notFound_throwsException() {
    when(applicationRepository.findByApplicationCode("NOT_EXIST")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getApplicationByCode("NOT_EXIST"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LAB_APPLICATION_NOT_FOUND));
  }

  private SubmitLabApplicationRequest createValidRequest() {
    return new SubmitLabApplicationRequest(
        "Saigon Darkroom",
        "0123456789",
        "123 Nguyen Hue",
        "Quan 1",
        "TP Ho Chi Minh",
        "contact@saigondarkroom.vn",
        "0901234567",
        Set.of(1L),
        Set.of(1L),
        Set.of(1L));
  }

  private void mockActiveMasterData() {
    ServiceType st = new ServiceType();
    ReflectionTestUtils.setField(st, "id", 1L);
    st.setStatus(CatalogStatus.ACTIVE);
    when(serviceTypeRepository.findAllById(any())).thenReturn(List.of(st));

    FilmProcess fp = new FilmProcess();
    ReflectionTestUtils.setField(fp, "id", 1L);
    fp.setStatus(CatalogStatus.ACTIVE);
    when(filmProcessRepository.findAllById(any())).thenReturn(List.of(fp));

    FilmFormat ff = new FilmFormat();
    ReflectionTestUtils.setField(ff, "id", 1L);
    ff.setStatus(CatalogStatus.ACTIVE);
    when(filmFormatRepository.findAllById(any())).thenReturn(List.of(ff));
  }
}
