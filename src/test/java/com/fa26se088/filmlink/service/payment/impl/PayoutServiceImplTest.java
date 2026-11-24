package com.fa26se088.filmlink.service.payment.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.payment.request.ExecutePayoutRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.payment.Payout;
import com.fa26se088.filmlink.entity.payment.enums.PayoutRecipientType;
import com.fa26se088.filmlink.entity.payment.enums.PayoutStatus;
import com.fa26se088.filmlink.mapper.payment.PayoutMapper;
import com.fa26se088.filmlink.repository.payment.PayoutRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

  @Mock private PayoutRepository payoutRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private AuditLogService auditLogService;

  private final PayoutMapper payoutMapper = new PayoutMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

  private PayoutServiceImpl payoutService;

  private AuthenticatedAccount adminAccount;

  @BeforeEach
  void setUp() {
    payoutService = new PayoutServiceImpl(
        payoutRepository, payoutMapper, labAccessService, auditLogService, clock);

    adminAccount = new AuthenticatedAccount(1L, "admin@test.com", UserAccountRole.ADMIN);
  }

  @Test
  @DisplayName("Admin tạo đợt Payout thành công ở trạng thái PENDING")
  void createPayoutForLab_success() {
    when(payoutRepository.save(any(Payout.class))).thenAnswer(i -> {
      Payout p = i.getArgument(0);
      ReflectionTestUtils.setField(p, "id", 100L);
      return p;
    });

    PayoutResponse response = payoutService.createPayoutForLab(1L, 5000000L, adminAccount);

    assertNotNull(response);
    assertEquals(100L, response.id());
    assertEquals(5000000L, response.amountVnd());
    assertEquals(PayoutStatus.PENDING, response.status());
    assertEquals(PayoutRecipientType.FILM_LAB, response.recipientType());
    verify(payoutRepository).save(any(Payout.class));
  }

  @Test
  @DisplayName("Admin thực thi Payout thành công -> trạng thái PAID, đính kèm biên lai")
  void executePayout_success() {
    Payout payout = new Payout();
    ReflectionTestUtils.setField(payout, "id", 100L);
    payout.setPayoutCode("PO12345");
    payout.setAmountVnd(5000000L);
    payout.setStatus(PayoutStatus.PENDING);

    when(payoutRepository.findById(100L)).thenReturn(Optional.of(payout));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(i -> i.getArgument(0));

    ExecutePayoutRequest request = new ExecutePayoutRequest(
        "Vietcombank", "0123456789", "LAB OWNER TEST", "receipts/payout_100.jpg");

    PayoutResponse response = payoutService.executePayout(100L, request, adminAccount);

    assertNotNull(response);
    assertEquals(PayoutStatus.PAID, response.status());
    assertEquals("Vietcombank", response.bankName());
    assertEquals("receipts/payout_100.jpg", response.receiptBlobKey());
    assertNotNull(response.paidAt());
  }
}

