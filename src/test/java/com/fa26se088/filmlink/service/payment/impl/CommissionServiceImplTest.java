package com.fa26se088.filmlink.service.payment.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fa26se088.filmlink.dto.payment.ClosedOrderCommission;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.payment.Commission;
import com.fa26se088.filmlink.entity.payment.enums.CommissionCollectionMode;
import com.fa26se088.filmlink.entity.payment.enums.CommissionKind;
import com.fa26se088.filmlink.entity.payment.enums.CommissionStatus;
import com.fa26se088.filmlink.mapper.payment.CommissionMapper;
import com.fa26se088.filmlink.repository.payment.CommissionRepository;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommissionServiceImplTest {

  private static final Instant CLOSED_AT = Instant.parse("2026-11-10T08:00:00Z");

  @Mock private CommissionRepository commissionRepository;
  @Mock private LabAccessService labAccessService;
  @Mock private AuditLogService auditLogService;
  @Mock private SystemParameterService systemParameterService;

  private final CommissionMapper commissionMapper = new CommissionMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

  private CommissionServiceImpl commissionService;

  @BeforeEach
  void setUp() {
    commissionService =
        new CommissionServiceImpl(
            commissionRepository,
            commissionMapper,
            labAccessService,
            auditLogService,
            systemParameterService,
            clock);
  }

  private static ClosedOrderCommission command(Long orderId, String method, long value) {
    return new ClosedOrderCommission(orderId, 1L, List.of(7L), method, value, CLOSED_AT);
  }

  private Commission captureSaved() {
    ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
    verify(commissionRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("BR-10: đơn ONLINE -> DEDUCTED + SETTLED, đọc tỷ lệ từ SystemParameterService")
  void recordForClosedOrder_online_deductedAndSettled() {
    when(commissionRepository.existsByServiceOrderIdAndKind(100L, CommissionKind.ORDER))
        .thenReturn(false);
    when(systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE))
        .thenReturn(new BigDecimal("10.00"));
    when(commissionRepository.save(any(Commission.class)))
        .thenAnswer(
            i -> {
              Commission c = i.getArgument(0);
              ReflectionTestUtils.setField(c, "id", 1L);
              return c;
            });

    commissionService.recordForClosedOrder(command(100L, "ONLINE", 200_000L));

    Commission saved = captureSaved();
    assertEquals(CommissionKind.ORDER, saved.getKind());
    assertEquals(1L, saved.getFilmLabId());
    assertEquals(100L, saved.getServiceOrderId());
    assertEquals(200_000L, saved.getBaseAmountVnd());
    assertEquals(0, new BigDecimal("10.00").compareTo(saved.getRatePercent()));
    assertEquals(20_000L, saved.getAmountVnd());
    assertEquals(CommissionCollectionMode.DEDUCTED, saved.getCollectionMode());
    assertEquals(CommissionStatus.SETTLED, saved.getStatus());
    assertEquals(CLOSED_AT, saved.getEligibleAt());
  }

  @Test
  @DisplayName("BR-11: đơn PAY_AT_LAB -> DEBT + OPEN (chờ chốt kỳ nợ)")
  void recordForClosedOrder_payAtLab_debtAndOpen() {
    when(commissionRepository.existsByServiceOrderIdAndKind(101L, CommissionKind.ORDER))
        .thenReturn(false);
    when(systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE))
        .thenReturn(new BigDecimal("10.00"));
    when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

    commissionService.recordForClosedOrder(command(101L, "PAY_AT_LAB", 300_000L));

    Commission saved = captureSaved();
    assertEquals(30_000L, saved.getAmountVnd());
    assertEquals(CommissionCollectionMode.DEBT, saved.getCollectionMode());
    assertEquals(CommissionStatus.OPEN, saved.getStatus());
  }

  @Test
  @DisplayName("BR-11: đơn COD -> DEBT + OPEN")
  void recordForClosedOrder_cod_debtAndOpen() {
    when(commissionRepository.existsByServiceOrderIdAndKind(102L, CommissionKind.ORDER))
        .thenReturn(false);
    when(systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE))
        .thenReturn(new BigDecimal("10.00"));
    when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

    commissionService.recordForClosedOrder(command(102L, "COD", 150_000L));

    Commission saved = captureSaved();
    assertEquals(15_000L, saved.getAmountVnd());
    assertEquals(CommissionCollectionMode.DEBT, saved.getCollectionMode());
  }

  @Test
  @DisplayName("BR-38: đọc tỷ lệ hoa hồng động từ SystemParameterService (VD: 15%)")
  void recordForClosedOrder_customRateFromSystemParameter_usesConfiguredRate() {
    when(commissionRepository.existsByServiceOrderIdAndKind(105L, CommissionKind.ORDER))
        .thenReturn(false);
    when(systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE))
        .thenReturn(new BigDecimal("15.00"));
    when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

    commissionService.recordForClosedOrder(command(105L, "ONLINE", 200_000L));

    Commission saved = captureSaved();
    assertEquals(0, new BigDecimal("15.00").compareTo(saved.getRatePercent()));
    assertEquals(30_000L, saved.getAmountVnd());
  }

  @Test
  @DisplayName("Contract §4.6: gọi recordForClosedOrder 2 lần cho cùng đơn chỉ tạo 1 commission")
  void recordForClosedOrder_calledTwice_createsOnlyOne() {
    when(commissionRepository.existsByServiceOrderIdAndKind(103L, CommissionKind.ORDER))
        .thenReturn(false)
        .thenReturn(true);
    when(systemParameterService.getDecimal(SystemParameterKey.DEFAULT_COMMISSION_RATE))
        .thenReturn(new BigDecimal("10.00"));
    when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

    commissionService.recordForClosedOrder(command(103L, "ONLINE", 100_000L));
    commissionService.recordForClosedOrder(command(103L, "ONLINE", 100_000L));

    verify(commissionRepository, times(1)).save(any(Commission.class));
  }

  @Test
  @DisplayName("Idempotent: đơn đã có commission ORDER thì không ghi thêm")
  void recordForClosedOrder_existing_noSave() {
    when(commissionRepository.existsByServiceOrderIdAndKind(104L, CommissionKind.ORDER))
        .thenReturn(true);

    commissionService.recordForClosedOrder(command(104L, "ONLINE", 100_000L));

    verify(commissionRepository, never()).save(any());
  }
}
