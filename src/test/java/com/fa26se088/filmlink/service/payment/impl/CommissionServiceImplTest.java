package com.fa26se088.filmlink.service.payment.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.payment.response.CommissionResponse;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.mapper.payment.CommissionMapper;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import java.math.BigDecimal;
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
class CommissionServiceImplTest {

  @Mock private CommissionRepository commissionRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private AuditLogService auditLogService;

  private final CommissionMapper commissionMapper = new CommissionMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

  private CommissionServiceImpl commissionService;

  @BeforeEach
  void setUp() {
    commissionService = new CommissionServiceImpl(
        commissionRepository, commissionMapper, labAccessService, auditLogService, clock);
  }

  @Test
  @DisplayName("Tạo Commission cho đơn ONLINE -> mode DEDUCTED, status SETTLED")
  void createCommissionForOrder_online_deductedAndSettled() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 100L);
    order.setFilmLabId(1L);
    order.setFinalTotalVnd(200000L);
    order.setPaymentMethod(OrderPaymentMethod.ONLINE);
    order.setOrderCode("FL-TEST100");

    when(commissionRepository.findByServiceOrderIdAndKind(100L, CommissionKind.ORDER))
        .thenReturn(Optional.empty());

    when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> {
      Commission c = i.getArgument(0);
      ReflectionTestUtils.setField(c, "id", 1L);
      return c;
    });

    CommissionResponse response = commissionService.createCommissionForOrder(order);

    assertNotNull(response);
    assertEquals(1L, response.id());
    assertEquals(20000L, response.amountVnd()); // 10% của 200,000 VND
    assertEquals(CommissionCollectionMode.DEDUCTED, response.collectionMode());
    assertEquals(CommissionStatus.SETTLED, response.status());
    verify(commissionRepository).save(any(Commission.class));
  }

  @Test
  @DisplayName("Tạo Commission cho đơn PAY_AT_LAB -> mode DEBT, status OPEN (vào kỳ nợ)")
  void createCommissionForOrder_payAtLab_debtAndOpen() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 101L);
    order.setFilmLabId(2L);
    order.setEstimatedTotalVnd(300000L);
    order.setFinalTotalVnd(300000L);
    order.setPaymentMethod(OrderPaymentMethod.PAY_AT_LAB);
    order.setOrderCode("FL-TEST101");

    when(commissionRepository.findByServiceOrderIdAndKind(101L, CommissionKind.ORDER))
        .thenReturn(Optional.empty());

    when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> {
      Commission c = i.getArgument(0);
      ReflectionTestUtils.setField(c, "id", 2L);
      return c;
    });

    CommissionResponse response = commissionService.createCommissionForOrder(order);

    assertNotNull(response);
    assertEquals(2L, response.id());
    assertEquals(30000L, response.amountVnd()); // 10% của 300,000 VND
    assertEquals(CommissionCollectionMode.DEBT, response.collectionMode());
    assertEquals(CommissionStatus.OPEN, response.status());
  }

  @Test
  @DisplayName("Idempotent: Đơn hàng đã có Commission thì trả về bản ghi cũ không tạo mới")
  void createCommissionForOrder_idempotent() {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", 102L);

    Commission existing = new Commission();
    ReflectionTestUtils.setField(existing, "id", 55L);
    existing.setKind(CommissionKind.ORDER);
    existing.setServiceOrderId(102L);
    existing.setAmountVnd(15000L);

    when(commissionRepository.findByServiceOrderIdAndKind(102L, CommissionKind.ORDER))
        .thenReturn(Optional.of(existing));

    CommissionResponse response = commissionService.createCommissionForOrder(order);

    assertEquals(55L, response.id());
    assertEquals(15000L, response.amountVnd());
    verify(commissionRepository, never()).save(any());
  }
}

