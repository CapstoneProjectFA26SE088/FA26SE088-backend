package com.fa26se088.filmlink.service.payment.impl;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.payment.request.UpdatePayoutAccountRequest;
import com.fa26se088.filmlink.dto.payment.response.PayoutAccountResponse;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
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
import com.fa26se088.filmlink.service.payment.PayoutService;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Thực thi quản lý tài khoản nhận tiền, sinh đợt giải ngân và ghi nhận chi trả cho Lab (UC35, UC36). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  private final PayoutRepository payoutRepository;
  private final PayoutItemRepository payoutItemRepository;
  private final PayoutAccountRepository payoutAccountRepository;
  private final CommissionRepository commissionRepository;
  private final PaymentService paymentService;
  private final PayoutMapper payoutMapper;
  private final PayoutAccountMapper payoutAccountMapper;
  private final FilmLabService filmLabService;
  private final LabAccessService labAccessService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final StorageClient storageClient;
  private final Clock clock;

  /**
   * BR-46: mỗi lab một đợt {@code PENDING} gồm các đơn ONLINE đã CLOSED và qua thời gian re-scan
   * ({@code eligible_at <= now}). Số tiền mỗi dòng = tiền thực nhận của đơn − hoa hồng.
   *
   * <p>Ghi chú căn chỉnh DB ({@code ck_commissions_billing}): hoa hồng ONLINE mang trạng thái
   * {@code DEDUCTED + SETTLED} từ thời điểm đóng đơn, nên điều kiện đơn chưa được giải ngân là
   * {@code service_order_id} chưa xuất hiện trong {@code payout_items} (được bảo vệ bởi unique
   * index {@code uq_payout_items_order}).
   */
  @Override
  @Transactional
  public int generateDuePayouts() {
    Instant now = clock.instant();
    List<Commission> due =
        commissionRepository.findPayable(
            CommissionKind.ORDER,
            CommissionCollectionMode.DEDUCTED,
            CommissionStatus.SETTLED,
            now);

    Map<Long, List<Commission>> byLab =
        due.stream()
            .collect(
                Collectors.groupingBy(
                    Commission::getFilmLabId, LinkedHashMap::new, Collectors.toList()));

    int created = 0;
    for (Map.Entry<Long, List<Commission>> entry : byLab.entrySet()) {
      if (createPayoutForLab(entry.getKey(), entry.getValue())) {
        created++;
      }
    }
    log.info(
        "[PayoutService] Sinh payout: {} hoa hồng đủ điều kiện, {} lab, tạo {} đợt",
        due.size(),
        byLab.size(),
        created);
    return created;
  }

  /**
   * UC35, BR-31: biên lai chuyển khoản bắt buộc; thông tin ngân hàng được sao chép vào bản ghi
   * {@code payouts} để việc đổi tài khoản nhận tiền về sau không làm thay đổi lịch sử giải ngân.
   */
  @Override
  @Transactional
  public PayoutResponse markPaid(
      Long payoutId, MultipartFile receipt, AuthenticatedAccount adminAccount) {
    requireAdmin(adminAccount);
    if (receipt == null || receipt.isEmpty()) {
      throw new BusinessException(
          ErrorCode.PAYOUT_RECEIPT_REQUIRED, "Cần đính kèm biên lai chuyển khoản.");
    }
    Payout payout =
        payoutRepository
            .findByIdForUpdate(payoutId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.PAYOUT_NOT_FOUND, "Không tìm thấy đợt chi trả."));
    if (payout.getStatus() != PayoutStatus.PENDING) {
      throw new BusinessException(
          ErrorCode.PAYOUT_INVALID_STATE,
          "Chỉ ghi nhận chuyển khoản cho đợt chi trả đang chờ (PENDING).");
    }

    if (payout.getBankAccountNumber() == null && !copyBankDetails(payout)) {
      throw new BusinessException(
          ErrorCode.PAYOUT_ACCOUNT_REQUIRED,
          "Người nhận chưa khai báo tài khoản ngân hàng để chuyển khoản.");
    }

    String blobKey = uploadReceipt(receipt);
    Instant now = clock.instant();
    Map<String, Object> before = Map.of("status", payout.getStatus().name());

    payout.setStatus(PayoutStatus.PAID);
    payout.setReceiptBlobKey(blobKey);
    payout.setExecutedByAccountId(adminAccount.id());
    payout.setPaidAt(now);
    Payout saved = payoutRepository.save(payout);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.PAYOUT_EXECUTED,
            AuditTargetType.PAYOUT,
            saved.getId(),
            "Admin ghi nhận chuyển khoản "
                + saved.getAmountVnd()
                + " VND cho "
                + saved.getPayoutCode(),
            before,
            Map.of("status", saved.getStatus().name(), "paidAt", now.toString())));

    notifyRecipient(saved);
    log.info(
        "[PayoutService] Payout {} đã chi trả, số tiền {} VND",
        saved.getPayoutCode(),
        saved.getAmountVnd());
    return payoutMapper.toResponse(saved, payoutItemRepository.findByPayoutId(saved.getId()));
  }

  @Override
  @Transactional(readOnly = true)
  public PayoutAccountResponse getMyPayoutAccount(AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    PayoutAccount account =
        payoutAccountRepository
            .findByFilmLabId(lab.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.PAYOUT_ACCOUNT_NOT_FOUND,
                        "Lab chưa khai báo tài khoản nhận tiền."));
    return payoutAccountMapper.toResponse(account);
  }

  @Override
  @Transactional
  public PayoutAccountResponse updateMyPayoutAccount(
      UpdatePayoutAccountRequest request, AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    PayoutAccount account =
        payoutAccountRepository
            .findByFilmLabId(lab.getId())
            .orElseGet(
                () -> {
                  PayoutAccount created = new PayoutAccount();
                  created.setFilmLabId(lab.getId());
                  return created;
                });
    account.setBankName(request.bankName().trim());
    account.setBankAccountNumber(request.bankAccountNumber());
    account.setAccountHolderName(request.accountHolderName().trim());
    PayoutAccount saved = payoutAccountRepository.save(account);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.PAYOUT_ACCOUNT_UPDATED,
            AuditTargetType.PAYOUT_ACCOUNT,
            saved.getId(),
            "Lab #" + lab.getId() + " cập nhật tài khoản nhận tiền",
            null,
            Map.of("bankName", saved.getBankName())));
    return payoutAccountMapper.toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PayoutResponse> getMyLabPayouts(
      Pageable pageable, AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    return mapPageWithItems(payoutRepository.findByFilmLabId(lab.getId(), pageable));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PayoutResponse> getAllPayouts(
      PayoutStatus status, Pageable pageable, AuthenticatedAccount adminAccount) {
    requireAdmin(adminAccount);
    Page<Payout> page =
        status != null
            ? payoutRepository.findByStatus(status, pageable)
            : payoutRepository.findAll(pageable);
    return mapPageWithItems(page);
  }

  @Override
  @Transactional(readOnly = true)
  public PayoutResponse getPayoutById(Long payoutId, AuthenticatedAccount account) {
    Payout payout =
        payoutRepository
            .findById(payoutId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.PAYOUT_NOT_FOUND, "Không tìm thấy đợt chi trả."));
    if (account.role() != UserAccountRole.ADMIN) {
      FilmLab lab = labAccessService.requireOwnedLab(account.id());
      if (!Objects.equals(payout.getFilmLabId(), lab.getId())) {
        throw new BusinessException(
            ErrorCode.LAB_ACCESS_DENIED, "Bạn không có quyền xem đợt chi trả này.");
      }
    }
    return payoutMapper.toResponse(payout, payoutItemRepository.findByPayoutId(payout.getId()));
  }

  private Page<PayoutResponse> mapPageWithItems(Page<Payout> page) {
    List<Long> payoutIds = page.getContent().stream().map(Payout::getId).toList();
    Map<Long, List<PayoutItem>> itemsByPayout =
        payoutIds.isEmpty()
            ? Map.of()
            : payoutItemRepository.findByPayoutIdIn(payoutIds).stream()
                .collect(Collectors.groupingBy(item -> item.getPayout().getId()));
    return page.map(
        payout ->
            payoutMapper.toResponse(
                payout, itemsByPayout.getOrDefault(payout.getId(), List.of())));
  }

  private boolean createPayoutForLab(Long filmLabId, List<Commission> commissions) {
    List<PayoutItem> items = new ArrayList<>();
    long total = 0;
    for (Commission commission : commissions) {
      long amount =
          paymentService.netPaidForOrder(commission.getServiceOrderId())
              - commission.getAmountVnd();
      if (amount <= 0) {
        log.warn(
            "[PayoutService] Bỏ qua đơn #{}: số tiền chi trả {} VND không dương",
            commission.getServiceOrderId(),
            amount);
        continue;
      }
      PayoutItem item = new PayoutItem();
      item.setServiceOrderId(commission.getServiceOrderId());
      item.setAmountVnd(amount);
      items.add(item);
      total += amount;
    }
    if (items.isEmpty()) {
      return false;
    }

    Payout payout = new Payout();
    payout.setPayoutCode("PO" + generateCode(10));
    payout.setRecipientType(PayoutRecipientType.FILM_LAB);
    payout.setFilmLabId(filmLabId);
    payout.setAmountVnd(total);
    payout.setStatus(PayoutStatus.PENDING);
    copyBankDetails(payout);
    Payout saved = payoutRepository.save(payout);

    items.forEach(item -> item.setPayout(saved));
    payoutItemRepository.saveAll(items);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.PAYOUT_CREATED,
            AuditTargetType.PAYOUT,
            saved.getId(),
            "Tạo payout "
                + saved.getPayoutCode()
                + " cho lab #"
                + filmLabId
                + ": "
                + total
                + " VND, "
                + items.size()
                + " đơn",
            null,
            Map.of("status", saved.getStatus().name(), "amountVnd", total)));
    return true;
  }

  private boolean copyBankDetails(Payout payout) {
    PayoutAccount account =
        payout.getRecipientType() == PayoutRecipientType.FILM_LAB
            ? payoutAccountRepository.findByFilmLabId(payout.getFilmLabId()).orElse(null)
            : payoutAccountRepository
                .findByUserAccountId(payout.getRecipientAccountId())
                .orElse(null);
    if (account == null) {
      return false;
    }
    payout.setBankName(account.getBankName());
    payout.setBankAccountNumber(account.getBankAccountNumber());
    payout.setAccountHolderName(account.getAccountHolderName());
    return true;
  }

  private String uploadReceipt(MultipartFile receipt) {
    try (InputStream in = receipt.getInputStream()) {
      return storageClient.upload(
          StoragePurpose.PAYOUT_RECEIPT, receipt.getOriginalFilename(), in, receipt.getSize());
    } catch (IOException ex) {
      throw new BusinessException(
          ErrorCode.STORAGE_UNAVAILABLE, "Không đọc được file biên lai.");
    }
  }

  private void notifyRecipient(Payout payout) {
    Long recipientId =
        payout.getRecipientType() == PayoutRecipientType.FILM_LAB
            ? filmLabService.getLabForOrdering(payout.getFilmLabId()).ownerAccountId()
            : payout.getRecipientAccountId();
    notificationService.notify(
        recipientId,
        NotificationType.PAYMENT,
        "Đã chi trả " + payout.getPayoutCode(),
        "Sàn đã chuyển khoản "
            + payout.getAmountVnd()
            + " VND cho đợt chi trả "
            + payout.getPayoutCode()
            + ".",
        null);
  }

  private void requireAdmin(AuthenticatedAccount account) {
    if (account.role() != UserAccountRole.ADMIN) {
      throw new BusinessException(
          ErrorCode.ACCESS_DENIED, "Chỉ Quản trị viên mới được thực hiện thao tác này.");
    }
  }

  private String generateCode(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    }
    return sb.toString();
  }
}
