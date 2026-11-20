package com.fa26se088.filmlink.service.payment.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.payment.request.ReportDebtPaymentRequest;
import com.fa26se088.filmlink.dto.payment.response.CommissionDebtResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.CommissionDebt;
import com.fa26se088.filmlink.entity.payment.Payment;
import com.fa26se088.filmlink.entity.payment.enums.CommissionDebtStatus;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.mapper.payment.CommissionDebtMapper;
import com.fa26se088.filmlink.repository.payment.CommissionDebtRepository;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.repository.payment.PaymentRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
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

  private final CommissionDebtMapper debtMapper = new CommissionDebtMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

  private CommissionDebtServiceImpl debtService;

  private AuthenticatedAccount labOwnerAccount;
  private AuthenticatedAccount adminAccount;

  @BeforeEach
  void setUp() {
    debtService = new CommissionDebtServiceImpl(
        debtRepository, commissionRepository, paymentRepository, debtMapper, labAccessService, auditLogService, clock);

    labOwnerAccount = new AuthenticatedAccount(20L, "labowner@test.com", UserAccountRole.FILM_LAB_OWNER);
    adminAccount = new AuthenticatedAccount(1L, "admin@test.com", UserAccountRole.ADMIN);
  }

  @Test
  @DisplayName("Tạo kỳ nợ thành công khi có các dòng hoa hồng DEBT chưa chốt")
  void generateMonthlyDebt_success() {
    LocalDate period = LocalDate.of(2026, 10, 1);

    when(debtRepository.findByFilmLabIdAndBillingPeriod(1L, period))
        .thenReturn(Optional.empty());

    Commission c1 = new Commission();
    ReflectionTestUtils.setField(c1, "id", 10L);
    c1.setAmountVnd(30000L);
    c1.setStatus(CommissionStatus.OPEN);

    Commission c2 = new Commission();
    ReflectionTestUtils.setField(c2, "id", 11L);
    c2.setAmountVnd(20000L);
    c2.setStatus(CommissionStatus.OPEN);

    when(commissionRepository.findByFilmLabIdAndStatusInAndEligibleAtBefore(any(), any(), any()))
        .thenReturn(List.of(c1, c2));

    when(debtRepository.save(any(CommissionDebt.class))).thenAnswer(i -> {
      CommissionDebt d = i.getArgument(0);
      ReflectionTestUtils.setField(d, "id", 100L);
      return d;
    });

    CommissionDebtResponse response = debtService.generateMonthlyDebt(1L, period);

    assertNotNull(response);
    assertEquals(100L, response.id());
    assertEquals(50000L, response.totalAmountVnd());
    assertEquals(CommissionDebtStatus.UNPAID, response.status());
    verify(debtRepository).save(any(CommissionDebt.class));
    verify(commissionRepository).save(c1);
    verify(commissionRepository).save(c2);
  }

  @Test
  @DisplayName("Không có hoa hồng nợ trong tháng -> Không tạo kỳ nợ (trả về null)")
  void generateMonthlyDebt_noCommissions_returnsNull() {
    LocalDate period = LocalDate.of(2026, 10, 1);

    when(debtRepository.findByFilmLabIdAndBillingPeriod(1L, period))
        .thenReturn(Optional.empty());

    when(commissionRepository.findByFilmLabIdAndStatusInAndEligibleAtBefore(any(), any(), any()))
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
    debt.setTotalAmountVnd(50000L);
    debt.setPaidAmountVnd(0L);
    debt.setStatus(CommissionDebtStatus.UNPAID);

    when(debtRepository.findById(100L)).thenReturn(Optional.of(debt));
    FilmLab lab = new FilmLab();
    ReflectionTestUtils.setField(lab, "id", 1L);
    when(labAccessService.requireOwnedLab(20L)).thenReturn(lab);

    ReportDebtPaymentRequest request = new ReportDebtPaymentRequest("receipts/october.jpg", "Đã nộp xong");
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
    debt.setTotalAmountVnd(50000L);
    debt.setStatus(CommissionDebtStatus.UNPAID);

    when(debtRepository.findById(100L)).thenReturn(Optional.of(debt));
    when(paymentRepository.findAll()).thenReturn(List.of());
    when(debtRepository.save(any(CommissionDebt.class))).thenAnswer(i -> i.getArgument(0));

    CommissionDebtResponse response = debtService.confirmDebtPayment(100L, adminAccount);

    assertNotNull(response);
    assertEquals(CommissionDebtStatus.PAID, response.status());
    assertEquals(50000L, response.paidAmountVnd());
    assertNotNull(response.settledAt());
  }
}

