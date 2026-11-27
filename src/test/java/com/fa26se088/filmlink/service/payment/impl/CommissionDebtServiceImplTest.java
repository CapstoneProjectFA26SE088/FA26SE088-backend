package com.fa26se088.filmlink.service.payment.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.payment.request.ReportDebtPaymentRequest;
import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.CommissionDebt;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.mapper.payment.CommissionDebtMapper;
import com.fa26se088.filmlink.repository.payment.CommissionDebtRepository;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.repository.payment.PaymentRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommissionDebtServiceImplTest {

  @Mock private CommissionDebtRepository debtRepository;
  @Mock private CommissionRepository commissionRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private AuditLogService auditLogService;
  @Mock private SystemParameterService systemParameterService;

  private final CommissionDebtMapper debtMapper = new CommissionDebtMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC);

  private CommissionDebtServiceImpl debtService;

  private AuthenticatedAccount labOwnerAccount;
  private AuthenticatedAccount adminAccount;

  @BeforeEach
  void setUp() {
    debtService =
        new CommissionDebtServiceImpl(
            debtRepository,
            commissionRepository,
            paymentRepository,
            debtMapper,
            labAccessService,
            auditLogService,
            systemParameterService,
            clock);

    labOwnerAccount =
        new AuthenticatedAccount(20L, "labowner@test.com", UserAccountRole.FILM_LAB_OWNER);
    adminAccount = new AuthenticatedAccount(1L, "admin@test.com", UserAccountRole.ADMIN);
  }

  @Test
  @DisplayName(
      "TC-F18-01 lab with 2 closed PAY_AT_LAB orders in October (300,000 and 200,000) -> commissions BILLED, one debt UNPAID 50,000 due 10/11")
  void tcF1801_generateMonthlyDebt_octoberTwoOrders_creates50kDebtDueNov10() {
    LocalDate period = LocalDate.of(2026, 10, 1);

    when(debtRepository.findByFilmLabIdAndBillingPeriod(1L, period)).thenReturn(Optional.empty());

    Commission c1 = new Commission();
    ReflectionTestUtils.setField(c1, "id", 10L);
    c1.setCollectionMode(CommissionCollectionMode.DEBT);
    c1.setBaseAmountVnd(300_000L);
    c1.setAmountVnd(30_000L);
    c1.setStatus(CommissionStatus.OPEN);

    Commission c2 = new Commission();
    ReflectionTestUtils.setField(c2, "id", 11L);
    c2.setCollectionMode(CommissionCollectionMode.DEBT);
    c2.setBaseAmountVnd(200_000L);
    c2.setAmountVnd(20_000L);
    c2.setStatus(CommissionStatus.OPEN);

    when(commissionRepository.findByFilmLabIdAndCollectionModeAndStatusInAndEligibleAtBefore(
            eq(1L), eq(CommissionCollectionMode.DEBT), any(), any()))
        .thenReturn(List.of(c1, c2));
    when(systemParameterService.getInteger(SystemParameterKey.BILLING_DUE_DAY_OF_MONTH))
        .thenReturn(10);

    when(debtRepository.save(any(CommissionDebt.class)))
        .thenAnswer(
            i -> {
              CommissionDebt d = i.getArgument(0);
              ReflectionTestUtils.setField(d, "id", 100L);
              return d;
            });

    CommissionDebtResponse response = debtService.generateMonthlyDebt(1L, period);

    assertNotNull(response);
    assertEquals(100L, response.id());
    assertEquals(50_000L, response.totalAmountVnd());
    assertEquals(LocalDate.of(2026, 11, 10), response.dueDate());
    assertEquals(CommissionDebtStatus.UNPAID, response.status());
    assertEquals(CommissionStatus.BILLED, c1.getStatus());
    assertEquals(100L, c1.getCommissionDebtId());
    assertEquals(CommissionStatus.BILLED, c2.getStatus());
    assertEquals(100L, c2.getCommissionDebtId());
    verify(debtRepository).save(any(CommissionDebt.class));
    verify(commissionRepository).save(c1);
    verify(commissionRepository).save(c2);
  }

  @Test
  @DisplayName("TC-SM10-01 UC32 period closed, net commission > 0 -> UNPAID")
  void tcSm1001_periodClosed_netCommissionPositive_createsUnpaidDebt() {
    LocalDate period = LocalDate.of(2026, 10, 1);
    when(debtRepository.findByFilmLabIdAndBillingPeriod(2L, period)).thenReturn(Optional.empty());

    Commission c = new Commission();
    ReflectionTestUtils.setField(c, "id", 12L);
    c.setCollectionMode(CommissionCollectionMode.DEBT);
    c.setAmountVnd(22_000L);
    c.setStatus(CommissionStatus.OPEN);

    when(commissionRepository.findByFilmLabIdAndCollectionModeAndStatusInAndEligibleAtBefore(
            eq(2L), eq(CommissionCollectionMode.DEBT), any(), any()))
        .thenReturn(List.of(c));
    when(systemParameterService.getInteger(SystemParameterKey.BILLING_DUE_DAY_OF_MONTH))
        .thenReturn(10);
    when(debtRepository.save(any(CommissionDebt.class)))
        .thenAnswer(
            i -> {
              CommissionDebt d = i.getArgument(0);
              ReflectionTestUtils.setField(d, "id", 101L);
              return d;
            });

    CommissionDebtResponse response = debtService.generateMonthlyDebt(2L, period);

    assertNotNull(response);
    assertEquals(CommissionDebtStatus.UNPAID, response.status());
    assertEquals(22_000L, response.totalAmountVnd());
  }

  @Test
  @DisplayName("Không có hoa hồng nợ trong tháng -> Không tạo kỳ nợ (trả về null)")
  void generateMonthlyDebt_noCommissions_returnsNull() {
    LocalDate period = LocalDate.of(2026, 10, 1);

    when(debtRepository.findByFilmLabIdAndBillingPeriod(1L, period)).thenReturn(Optional.empty());

    when(commissionRepository.findByFilmLabIdAndCollectionModeAndStatusInAndEligibleAtBefore(
            eq(1L), eq(CommissionCollectionMode.DEBT), any(), any()))
        .thenReturn(List.of());

    CommissionDebtResponse response = debtService.generateMonthlyDebt(1L, period);

    assertNull(response);
  }

  @Test
  @DisplayName("Lab báo nộp nợ hoa hồng -> tạo Payment BANK_TRANSFER PENDING thành công")
  void reportDebtPayment_success() {
    CommissionDebt debt = new CommissionDebt();
    ReflectionTestUtils.setField(debt, "id", 100L);
    debt.setFilmLabId(1L);
    debt.setTotalAmountVnd(50_000L);
    debt.setPaidAmountVnd(0L);
    debt.setStatus(CommissionDebtStatus.UNPAID);

    when(debtRepository.findById(100L)).thenReturn(Optional.of(debt));
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.requireOwnedLab(20L)).thenReturn(lab);

    ReportDebtPaymentRequest request =
        new ReportDebtPaymentRequest("receipts/october.jpg", "Đã nộp xong");
    CommissionDebtResponse response = debtService.reportDebtPayment(100L, request, labOwnerAccount);

    assertNotNull(response);
    verify(paymentRepository).save(any(Payment.class));
  }

  @Test
  @DisplayName("Admin xác nhận thanh toán nợ -> chuyển trạng thái kỳ nợ sang PAID")
  void confirmDebtPayment_admin_success() {
    CommissionDebt debt = new CommissionDebt();
    ReflectionTestUtils.setField(debt, "id", 100L);
    debt.setFilmLabId(1L);
    debt.setTotalAmountVnd(50_000L);
    debt.setPaidAmountVnd(0L);
    debt.setStatus(CommissionDebtStatus.UNPAID);

    when(debtRepository.findById(100L)).thenReturn(Optional.of(debt));
    when(debtRepository.save(any(CommissionDebt.class))).thenAnswer(i -> i.getArgument(0));

    CommissionDebtResponse response = debtService.confirmDebtPayment(100L, adminAccount);

    assertEquals(CommissionDebtStatus.PAID, response.status());
    assertEquals(50_000L, response.paidAmountVnd());
    assertNotNull(response.settledAt());
  }
}
