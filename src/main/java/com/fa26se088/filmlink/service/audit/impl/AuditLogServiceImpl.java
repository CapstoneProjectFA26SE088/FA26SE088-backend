package com.fa26se088.filmlink.service.audit.impl;

import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.audit.response.AuditLogResponse;
import com.fa26se088.filmlink.dto.common.PageResponse;
import com.fa26se088.filmlink.entity.audit.AuditLog;
import com.fa26se088.filmlink.entity.audit.enums.AuditOutcome;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.mapper.audit.AuditLogMapper;
import com.fa26se088.filmlink.repository.audit.AuditLogRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.security.CurrentAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

  /** Mới nhất trước (khớp {@code ix_audit_logs_target}); {@code id} giữ thứ tự ổn định khi trùng giờ. */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

  private static final Pattern IPV4 =
      Pattern.compile(
          "(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)");

  /** Có ':' thì {@link InetAddress#getByName} coi là literal (sai thì ném lỗi), không tra DNS. */
  private static final Pattern IPV6_LIKE =
      Pattern.compile("(?=[^:]*:)[0-9a-fA-F:][0-9a-fA-F:.]*");

  private final AuditLogRepository auditLogRepository;
  private final AuditLogMapper auditLogMapper;

  /** BR-32: propagation REQUIRED — rollback cùng nghiệp vụ. */
  @Override
  @Transactional
  public void recordSuccess(AuditEntry entry) {
    auditLogRepository.save(toEntity(entry, AuditOutcome.SUCCESS));
  }

  /** BR-32: REQUIRES_NEW — vẫn lưu khi transaction nghiệp vụ rollback. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(AuditEntry entry) {
    auditLogRepository.save(toEntity(entry, AuditOutcome.FAILURE));
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<AuditLogResponse> search(
      AuditTargetType targetType, Long targetId, Pageable pageable) {
    Pageable sorted =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    return PageResponse.from(
        auditLogRepository.findAll(AuditLogRepository.byTarget(targetType, targetId), sorted),
        auditLogMapper::toResponse);
  }

  /**
   * Actor và role lấy từ tài khoản đang đăng nhập ({@link CurrentAccount#find()}), tức role tại
   * thời điểm thao tác; không có tài khoản (System) thì cả hai null.
   */
  private static AuditLog toEntity(AuditEntry entry, AuditOutcome outcome) {
    AuthenticatedAccount actor = CurrentAccount.find().orElse(null);
    AuditLog log = new AuditLog();
    log.setOccurredAt(Instant.now());
    log.setActorAccountId(actor == null ? null : actor.id());
    log.setActorRole(actor == null ? null : actor.role());
    log.setAction(entry.action());
    log.setTargetType(entry.targetType());
    log.setTargetId(entry.targetId());
    log.setOutcome(outcome);
    log.setReason(entry.reason());
    log.setBeforeData(entry.before());
    log.setAfterData(entry.after());
    log.setClientIp(currentClientIp());
    return log;
  }

  /**
   * IP của client lấy từ {@code request.getRemoteAddr()} khi đang trong HTTP request; ngoài HTTP
   * request (Scheduler, listener chạy async) trả {@code null}.
   *
   * <p>Cố ý <b>không</b> đọc {@code X-Forwarded-For}: header này do client tự gửi được, đọc vào sẽ
   * cho phép giả mạo IP trong audit log. Khi deploy sau reverse proxy, cấu hình proxy tin cậy ở tầng
   * server (ví dụ {@code server.forward-headers-strategy}) để {@code getRemoteAddr()} trả đúng IP.
   */
  static InetAddress currentClientIp() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
      return null;
    }
    return parseIpLiteral(attrs.getRequest().getRemoteAddr());
  }

  /**
   * Chỉ nhận IP dạng số (IPv4/IPv6), có thể kèm port và ngoặc vuông. Chuỗi như {@code "abc"} hay
   * {@code "abc.de"} là tên máy, sẽ làm {@link InetAddress#getByName} tra DNS nên bị loại.
   *
   * <p>App Service gửi {@code X-Forwarded-For} dạng {@code ip:port}; {@code ForwardedHeaderFilter}
   * bọc mọi giá trị có ':' trong ngoặc vuông: {@code "[203.0.113.7:54321]"}, {@code "[2001:db8::1]"}.
   */
  static InetAddress parseIpLiteral(String address) {
    if (address == null) {
      return null;
    }
    String ip = stripBracketsAndPort(address);
    if (!IPV4.matcher(ip).matches() && !IPV6_LIKE.matcher(ip).matches()) {
      return null;
    }
    try {
      return InetAddress.getByName(ip);
    } catch (UnknownHostException ex) {
      return null;
    }
  }

  private static String stripBracketsAndPort(String address) {
    String ip = address.trim();
    int closing = ip.indexOf(']');
    if (ip.startsWith("[") && closing > 0) {
      ip = ip.substring(1, closing);
    }
    // Đúng một ':' và có '.' phía trước là IPv4:port; IPv6 luôn có ít nhất hai ':'.
    int colon = ip.indexOf(':');
    boolean ipv4WithPort =
        colon > 0 && colon == ip.lastIndexOf(':') && ip.lastIndexOf('.', colon) >= 0;
    return ipv4WithPort ? ip.substring(0, colon) : ip;
  }
}
