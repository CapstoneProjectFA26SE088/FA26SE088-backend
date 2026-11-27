package com.fa26se088.filmlink.service.payment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.payment.request.UpdatePayoutAccountRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutAccountResponse;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.Payout;
import com.fa26se088.filmlink.entity.payment.PayoutAccount;
import com.fa26se088.filmlink.entity.payment.PayoutItem;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.entity.payment.enums.PayoutRecipientType;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.payment.PayoutAccountMapper;
import com.fa26se088.filmlink.mapper.payment.PayoutMapper;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.repository.payment.PayoutAccountRepository;
import com.fa26se088.filmlink.repository.payment.PayoutItemRepository;
import com.fa26se088.filmlink.repository.payment.PayoutRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import com.fa26se088.filmlink.service.payment.PaymentService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");
  private static final long LAB_ID = 5L;
  private static final long LAB_OWNER_ID = 50L;

  @Mock private PayoutRepository payoutRepository;
  @Mock private PayoutItemRepository payoutItemRepository;
  @Mock private PayoutAccountRepository payoutAccountRepository;
  @Mock private CommissionRepository commissionRepository;
  @Mock private PaymentService paymentService;
  @Mock private FilmLabService filmLabService;
  @Mock private LabAccessService labAccessService;
  @Mock private AuditLogService auditLogService;
  @Mock private NotificationService notificationService;
  @Mock private StorageClient storageClient;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private PayoutServiceImpl payoutService;
  private AuthenticatedAccount admin;

  @BeforeEach
  void setUp() {
    payoutService =
        new PayoutServiceImpl(
            payoutRepository,
            payoutItemRepository,
            payoutAccountRepository,
            commissionRepository,
            paymentService,
            new PayoutMapper(storageClient),
            new PayoutAccountMapper(),
            filmLabService,
            labAccessService,
            auditLogService,
            notificationService,
            storageClient,
            clock);
    admin = new AuthenticatedAccount(1L, "admin@test.com", UserAccountRole.ADMIN);
  }

  private static Commission commission(long orderId, long amountVnd) {
    Commission commission = new Commission();
    commission.setKind(CommissionKind.ORDER);
    commission.setFilmLabId(LAB_ID);
    commission.setServiceOrderId(orderId);
    commission.setAmountVnd(amountVnd);
    commission.setCollectionMode(CommissionCollectionMode.DEDUCTED);
    commission.setStatus(CommissionStatus.SETTLED);
    return commission;
  }

  private static PayoutAccount labAccount() {
    PayoutAccount account = new PayoutAccount();
    ReflectionTestUtils.setField(account, "id", 9L);
    account.setFilmLabId(LAB_ID);
    account.setBankName("Vietcombank");
    account.setBankAccountNumber("0123456789");
    account.setAccountHolderName("LAB OWNER");
    return account;
  }

  private void stubSavePayout() {
    when(payoutRepository.save(any(Payout.class)))
        .thenAnswer(
            i -> {
              Payout payout = i.getArgument(0);
              if (payout.getId() == null) {
                ReflectionTestUtils.setField(payout, "id", 100L);
              }
              return payout;
            });
  }

  @Test
  @DisplayName(
      "TC-F19-01 online order CLOSED 8 days ago -> payout job PENDING with amount = paid - commission")
  void tcF1901_generateDuePayouts_createsPendingPayoutWithNetAmount() {
    when(commissionRepository.findPayable(
            CommissionKind.ORDER,
            CommissionCollectionMode.DEDUCTED,
            CommissionStatus.SETTLED,
            NOW))
        .thenReturn(List.of(commission(10L, 22_000L), commission(11L, 11_000L)));
    when(paymentService.netPaidForOrder(10L)).thenReturn(220_000L);
    when(paymentService.netPaidForOrder(11L)).thenReturn(110_000L);
    when(payoutAccountRepository.findByFilmLabId(LAB_ID)).thenReturn(Optional.of(labAccount()));
    stubSavePayout();

    int created = payoutService.generateDuePayouts();

    assertThat(created).isEqualTo(1);
    ArgumentCaptor<Payout> payoutCaptor = ArgumentCaptor.forClass(Payout.class);
    verify(payoutRepository).save(payoutCaptor.capture());
    Payout payout = payoutCaptor.getValue();
    assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
    assertThat(payout.getRecipientType()).isEqualTo(PayoutRecipientType.FILM_LAB);
    assertThat(payout.getFilmLabId()).isEqualTo(LAB_ID);
    assertThat(payout.getAmountVnd()).isEqualTo(198_000L + 99_000L);
    assertThat(payout.getBankAccountNumber()).isEqualTo("0123456789");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PayoutItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
    verify(payoutItemRepository).saveAll(itemsCaptor.capture());
    assertThat(itemsCaptor.getValue())
        .extracting(PayoutItem::getServiceOrderId, PayoutItem::getAmountVnd)
        .containsExactly(Tuple.tuple(10L, 198_000L), Tuple.tuple(11L, 99_000L));
  }

  @Test
  @DisplayName("TC-F19-02 payout job idempotent: không còn hoa hồng đủ điều kiện thì không tạo payout")
  void generateDuePayouts_nothingDue_createsNothing() {
    when(commissionRepository.findPayable(any(), any(), any(), eq(NOW))).thenReturn(List.of());

    assertThat(payoutService.generateDuePayouts()).isZero();

    verify(payoutRepository, never()).save(any());
  }

  @Test
  @DisplayName("Job bỏ qua đơn có số tiền chi trả không dương")
  void generateDuePayouts_nonPositiveAmount_skipped() {
    when(commissionRepository.findPayable(any(), any(), any(), eq(NOW)))
        .thenReturn(List.of(commission(12L, 30_000L)));
    when(paymentService.netPaidForOrder(12L)).thenReturn(30_000L);

    assertThat(payoutService.generateDuePayouts()).isZero();

    verify(payoutRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "TC-WF02 DS-03 Admin records bank transfer with receipt -> payout PAID, signed receipt URL, notifies lab owner")
  void tcWf02_markPaid_withReceipt_becomesPaid() {
    Payout payout = pendingPayout();
    PayoutItem item = new PayoutItem();
    ReflectionTestUtils.setField(item, "id", 1L);
    item.setPayout(payout);
    item.setServiceOrderId(10L);
    item.setAmountVnd(297_000L);

    when(payoutRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payout));
    when(payoutAccountRepository.findByFilmLabId(LAB_ID)).thenReturn(Optional.of(labAccount()));
    when(payoutItemRepository.findByPayoutId(100L)).thenReturn(List.of(item));
    when(storageClient.upload(
            eq(StoragePurpose.PAYOUT_RECEIPT), eq("bill.png"), any(), eq(4L)))
        .thenReturn("payout-receipt/2026/11/abc.png");
    when(storageClient.signedUrl("payout-receipt/2026/11/abc.png"))
        .thenReturn(URI.create("http://localhost:8080/files/payout-receipt/2026/11/abc.png"));
    when(filmLabService.getLabForOrdering(LAB_ID))
        .thenReturn(new OrderingFilmLabResponse(LAB_ID, LAB_OWNER_ID, "Lab Test", true));
    stubSavePayout();

    PayoutResponse response =
        payoutService.markPaid(
            100L, new MockMultipartFile("receipt", "bill.png", "image/png", new byte[4]), admin);

    assertThat(response.status()).isEqualTo(PayoutStatus.PAID);
    assertThat(response.receiptBlobKey()).isEqualTo("payout-receipt/2026/11/abc.png");
    assertThat(response.receiptUrl())
        .isEqualTo("http://localhost:8080/files/payout-receipt/2026/11/abc.png");
    assertThat(response.executedByAccountId()).isEqualTo(1L);
    assertThat(response.paidAt()).isEqualTo(NOW);
    assertThat(response.bankAccountNumber()).isEqualTo("0123456789");
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().serviceOrderId()).isEqualTo(10L);
    verify(notificationService)
        .notify(eq(LAB_OWNER_ID), eq(NotificationType.PAYMENT), any(), any(), any());
  }

  @Test
  @DisplayName("UC35: chủ lab cập nhật tài khoản nhận tiền thành công")
  void updateMyPayoutAccount_createsOrUpdates() {
    AuthenticatedAccount owner =
        new AuthenticatedAccount(LAB_OWNER_ID, "owner@test.com", UserAccountRole.FILM_LAB_OWNER);
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", LAB_ID);
    when(labAccessService.requireOwnedLab(LAB_OWNER_ID)).thenReturn(lab);
    when(payoutAccountRepository.findByFilmLabId(LAB_ID)).thenReturn(Optional.empty());
    when(payoutAccountRepository.save(any(PayoutAccount.class)))
        .thenAnswer(
            i -> {
              PayoutAccount saved = i.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 9L);
              return saved;
            });

    PayoutAccountResponse res =
        payoutService.updateMyPayoutAccount(
            new UpdatePayoutAccountRequest(" Techcombank ", "19034567890", " NGUYEN VAN A "),
            owner);

    assertThat(res.bankName()).isEqualTo("Techcombank");
    assertThat(res.bankAccountNumber()).isEqualTo("19034567890");
    assertThat(res.accountHolderName()).isEqualTo("NGUYEN VAN A");
  }

  @Test
  @DisplayName("TC-F19-03 PAID không có biên lai bị từ chối")
  void markPaid_withoutReceipt_rejected() {
    assertThatThrownBy(() -> payoutService.markPaid(100L, null, admin))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYOUT_RECEIPT_REQUIRED));

    verify(payoutRepository, never()).findByIdForUpdate(any());
  }

  @Test
  @DisplayName("TC-F19-03 chủ lab không được ghi nhận chuyển khoản")
  void markPaid_byNonAdmin_forbidden() {
    AuthenticatedAccount owner =
        new AuthenticatedAccount(LAB_OWNER_ID, "owner@test.com", UserAccountRole.FILM_LAB_OWNER);

    assertThatThrownBy(
            () ->
                payoutService.markPaid(
                    100L,
                    new MockMultipartFile("receipt", "b.png", "image/png", new byte[2]),
                    owner))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
  }

  @Test
  @DisplayName("Payout đã PAID thì ghi nhận lần hai bị chặn")
  void markPaid_alreadyPaid_conflict() {
    Payout payout = pendingPayout();
    payout.setStatus(PayoutStatus.PAID);
    when(payoutRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payout));

    assertThatThrownBy(
            () ->
                payoutService.markPaid(
                    100L,
                    new MockMultipartFile("receipt", "b.png", "image/png", new byte[2]),
                    admin))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYOUT_INVALID_STATE));

    verify(storageClient, never()).upload(any(), any(), any(), anyLong());
  }

  @Test
  @DisplayName("Lab chưa khai báo tài khoản nhận tiền thì không ghi nhận PAID được")
  void markPaid_noBankAccount_rejected() {
    Payout payout = pendingPayout();
    when(payoutRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payout));
    when(payoutAccountRepository.findByFilmLabId(LAB_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                payoutService.markPaid(
                    100L,
                    new MockMultipartFile("receipt", "b.png", "image/png", new byte[2]),
                    admin))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYOUT_ACCOUNT_REQUIRED));
  }

  private static Payout pendingPayout() {
    Payout payout = new Payout();
    ReflectionTestUtils.setField(payout, "id", 100L);
    payout.setPayoutCode("PO12345");
    payout.setRecipientType(PayoutRecipientType.FILM_LAB);
    payout.setFilmLabId(LAB_ID);
    payout.setAmountVnd(297_000L);
    payout.setStatus(PayoutStatus.PENDING);
    return payout;
  }
}
