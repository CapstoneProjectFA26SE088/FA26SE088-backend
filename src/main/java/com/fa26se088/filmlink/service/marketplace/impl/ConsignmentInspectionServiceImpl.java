package com.fa26se088.filmlink.service.marketplace.impl;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.dto.audit.request.AuditEntry;
import com.fa26se088.filmlink.dto.lab.response.FilmLabOpeningHourResponse;
import com.fa26se088.filmlink.dto.lab.response.FilmLabProfileResponse;
import com.fa26se088.filmlink.dto.lab.response.OrderingFilmLabResponse;
import com.fa26se088.filmlink.dto.marketplace.request.ConditionCheckItemRequest;
import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentBookingRequest;
import com.fa26se088.filmlink.dto.marketplace.request.CreateConsignmentItemRequest;
import com.fa26se088.filmlink.dto.marketplace.request.RecordConditionReportRequest;
import com.fa26se088.filmlink.dto.marketplace.request.UpdateConsignmentSettingsRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConditionReportResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentBookingResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSettingsResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentSlotResponse;
import com.fa26se088.filmlink.entity.audit.enums.AuditAction;
import com.fa26se088.filmlink.entity.audit.enums.AuditTargetType;
import com.fa26se088.filmlink.entity.lab.FilmLab;
import com.fa26se088.filmlink.entity.marketplace.ConditionReport;
import com.fa26se088.filmlink.entity.marketplace.ConditionReportCheck;
import com.fa26se088.filmlink.entity.marketplace.ConditionReportCheckId;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentBooking;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItem;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentItemPhoto;
import com.fa26se088.filmlink.entity.marketplace.ConsignmentLabSettings;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionCheckCode;
import com.fa26se088.filmlink.entity.marketplace.enums.ConditionConclusion;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentBookingStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentClosingReason;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentItemStatus;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPhotoSource;
import com.fa26se088.filmlink.entity.masterdata.enums.SystemParameterKey;
import com.fa26se088.filmlink.entity.notification.enums.NotificationType;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import com.fa26se088.filmlink.mapper.marketplace.ConsignmentMapper;
import com.fa26se088.filmlink.repository.marketplace.ConditionReportCheckRepository;
import com.fa26se088.filmlink.repository.marketplace.ConditionReportRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentBookingRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemPhotoRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentItemRepository;
import com.fa26se088.filmlink.repository.marketplace.ConsignmentLabSettingsRepository;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.audit.AuditLogService;
import com.fa26se088.filmlink.service.lab.FilmLabService;
import com.fa26se088.filmlink.service.lab.LabAccessService;
import com.fa26se088.filmlink.service.marketplace.ConsignmentInspectionService;
import com.fa26se088.filmlink.service.masterdata.SystemParameterService;
import com.fa26se088.filmlink.service.notification.NotificationService;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Cấu hình nhận ký gửi, đặt lịch hẹn, check-in và kiểm định thiết bị (F-20, UC45, UC46). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsignmentInspectionServiceImpl implements ConsignmentInspectionService {

  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final LocalTime DEFAULT_OPENS_AT = LocalTime.of(8, 0);
  private static final LocalTime DEFAULT_CLOSES_AT = LocalTime.of(18, 0);
  private static final int MIN_INSPECTION_PHOTOS = 4;
  private static final int MAX_INSPECTION_PHOTOS = 20;
  private static final int DEFAULT_MAX_PENDING_BOOKINGS = 5;
  private static final int DEFAULT_COLLECTION_DAYS = 7;
  private static final List<ConsignmentBookingStatus> ACTIVE_SLOT_STATUSES =
      List.of(ConsignmentBookingStatus.BOOKED, ConsignmentBookingStatus.CHECKED_IN);

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  private final ConsignmentLabSettingsRepository settingsRepository;
  private final ConsignmentBookingRepository bookingRepository;
  private final ConsignmentItemRepository itemRepository;
  private final ConsignmentItemPhotoRepository itemPhotoRepository;
  private final ConditionReportRepository reportRepository;
  private final ConditionReportCheckRepository reportCheckRepository;
  private final ConsignmentMapper consignmentMapper;
  private final FilmLabService filmLabService;
  private final LabAccessService labAccessService;
  private final SystemParameterService systemParameterService;
  private final StorageClient storageClient;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public ConsignmentSettingsResponse getMySettings(AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    ConsignmentLabSettings settings =
        settingsRepository
            .findById(lab.getId())
            .orElseGet(() -> defaultUnsavedSettings(lab.getId()));
    return consignmentMapper.toSettingsResponse(settings);
  }

  /** UC45, BR-54: cấu hình nhận ký gửi của Lab; độ dài slot phải chia hết cho 15 phút. */
  @Override
  @Transactional
  public ConsignmentSettingsResponse updateMySettings(
      UpdateConsignmentSettingsRequest request, AuthenticatedAccount ownerAccount) {
    if (request.slotMinutes() % 15 != 0) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR, "Độ dài khung giờ (slotMinutes) phải là bội số của 15 phút.");
    }
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    Instant now = clock.instant();

    ConsignmentLabSettings settings =
        settingsRepository
            .findById(lab.getId())
            .orElseGet(
                () -> {
                  ConsignmentLabSettings created = new ConsignmentLabSettings();
                  created.setFilmLabId(lab.getId());
                  created.setCreatedAt(now);
                  return created;
                });

    settings.setAcceptingConsignments(Boolean.TRUE.equals(request.isAcceptingConsignments()));
    settings.setConsignmentFeeRate(request.consignmentFeeRate());
    settings.setSlotMinutes(request.slotMinutes());
    settings.setAppointmentsPerSlot(request.appointmentsPerSlot());
    settings.setUpdatedAt(now);

    ConsignmentLabSettings saved = settingsRepository.save(settings);
    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_SETTINGS_UPDATED,
            AuditTargetType.CONSIGNMENT_LAB_SETTINGS,
            saved.getFilmLabId(),
            "Lab #" + lab.getId() + " cập nhật cấu hình nhận ký gửi",
            null,
            Map.of(
                "acceptingConsignments", saved.isAcceptingConsignments(),
                "slotMinutes", saved.getSlotMinutes(),
                "appointmentsPerSlot", saved.getAppointmentsPerSlot())));
    return consignmentMapper.toSettingsResponse(saved);
  }

  /** UC45: sinh các slot trong ngày dựa trên giờ mở cửa của Lab và đếm số chỗ đã đặt. */
  @Override
  @Transactional(readOnly = true)
  public List<ConsignmentSlotResponse> getFreeSlots(Long filmLabId, LocalDate date) {
    if (date == null) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn ngày tra cứu lịch.");
    }
    OrderingFilmLabResponse lab = filmLabService.getLabForOrdering(filmLabId);
    ConsignmentLabSettings settings = settingsRepository.findById(filmLabId).orElse(null);
    if (settings == null || !settings.isAcceptingConsignments()) {
      return List.of();
    }

    OpeningWindow window = resolveOpeningWindow(lab.ownerAccountId(), date);
    if (window == null) {
      return List.of();
    }

    Instant dayStart = date.atTime(window.opensAt()).atZone(VIETNAM_ZONE).toInstant();
    Instant dayEnd = date.atTime(window.closesAt()).atZone(VIETNAM_ZONE).toInstant();

    Map<Instant, Long> countsBySlot =
        bookingRepository
            .findByFilmLabIdAndStatusInAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThan(
                filmLabId, ACTIVE_SLOT_STATUSES, dayStart, dayEnd)
            .stream()
            .collect(
                Collectors.groupingBy(
                    ConsignmentBooking::getSlotStartAt, Collectors.counting()));

    int slotMinutes = settings.getSlotMinutes();
    int capacity = settings.getAppointmentsPerSlot();
    List<ConsignmentSlotResponse> slots = new ArrayList<>();
    ZonedDateTime cursor = date.atTime(window.opensAt()).atZone(VIETNAM_ZONE);
    ZonedDateTime limit = date.atTime(window.closesAt()).atZone(VIETNAM_ZONE);

    while (!cursor.plusMinutes(slotMinutes).isAfter(limit)) {
      Instant start = cursor.toInstant();
      Instant end = cursor.plusMinutes(slotMinutes).toInstant();
      long booked = countsBySlot.getOrDefault(start, 0L);
      slots.add(new ConsignmentSlotResponse(start, end, capacity, booked, booked < capacity));
      cursor = cursor.plusMinutes(slotMinutes);
    }
    return slots;
  }

  /**
   * UC45, BR-49: kiểm tra Lab đang nhận ký gửi, khoá dòng cấu hình để đảm bảo sức chứa slot, tạo
   * lịch hẹn {@code BOOKED} và món đồ {@code AWAITING_DROP_OFF}.
   */
  @Override
  @Transactional
  public ConsignmentBookingResponse createBooking(
      CreateConsignmentBookingRequest request, AuthenticatedAccount sellerAccount) {
    OrderingFilmLabResponse lab = filmLabService.getLabForOrdering(request.filmLabId());
    if (Objects.equals(lab.ownerAccountId(), sellerAccount.id())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_NOT_ACCEPTING, "Chủ Lab không đặt lịch ký gửi vào chính Lab mình.");
    }

    ConsignmentLabSettings settings =
        settingsRepository
            .findByFilmLabIdForUpdate(request.filmLabId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_NOT_ACCEPTING,
                        "Film Lab này hiện chưa mở dịch vụ nhận ký gửi."));
    if (!settings.isAcceptingConsignments()) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_NOT_ACCEPTING, "Film Lab này hiện đang tạm ngưng nhận ký gửi.");
    }

    int maxPending =
        intParam(SystemParameterKey.CONSIGNMENT_MAX_PENDING_BOOKINGS, DEFAULT_MAX_PENDING_BOOKINGS);
    long currentPending =
        bookingRepository.countBySellerAccountIdAndStatus(
            sellerAccount.id(), ConsignmentBookingStatus.BOOKED);
    if (currentPending >= maxPending) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_BOOKING_LIMIT_EXCEEDED,
          "Bạn đã đạt giới hạn tối đa " + maxPending + " lịch hẹn đang chờ cùng lúc.");
    }

    Instant slotStart = request.slotStartAt();
    final Instant slotEnd = slotStart.plus(Duration.ofMinutes(settings.getSlotMinutes()));
    validateSlotAlignedWithLabWindow(lab.ownerAccountId(), slotStart, settings.getSlotMinutes());

    long bookedInSlot =
        bookingRepository.countByFilmLabIdAndSlotStartAtAndStatusIn(
            request.filmLabId(), slotStart, ACTIVE_SLOT_STATUSES);
    if (bookedInSlot >= settings.getAppointmentsPerSlot()) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_SLOT_FULL, "Khung giờ hẹn này đã đầy chỗ.");
    }

    CreateConsignmentItemRequest itemReq = request.item();
    final String trimmedName = stripRequired(itemReq.itemName(), 2, 150, "Tên thiết bị");
    final String trimmedDesc =
        stripRequired(itemReq.sellerDescription(), 20, 2000, "Mô tả tình trạng");

    ConsignmentBooking booking = new ConsignmentBooking();
    booking.setBookingCode(generateUniqueBookingCode());
    booking.setSellerAccountId(sellerAccount.id());
    booking.setFilmLabId(request.filmLabId());
    booking.setSlotStartAt(slotStart);
    booking.setSlotEndAt(slotEnd);
    booking.setStatus(ConsignmentBookingStatus.BOOKED);
    ConsignmentBooking savedBooking = bookingRepository.save(booking);

    ConsignmentItem item = new ConsignmentItem();
    item.setBooking(savedBooking);
    item.setItemType(itemReq.itemType());
    item.setItemName(trimmedName);
    item.setSellerDescription(trimmedDesc);
    item.setDesiredPriceVnd(itemReq.desiredPriceVnd());
    item.setStatus(ConsignmentItemStatus.AWAITING_DROP_OFF);
    ConsignmentItem savedItem = itemRepository.save(item);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_BOOKING_CREATED,
            AuditTargetType.CONSIGNMENT_BOOKING,
            savedBooking.getId(),
            "Đặt lịch ký gửi " + savedBooking.getBookingCode() + " tại Lab #" + request.filmLabId(),
            null,
            Map.of("status", savedBooking.getStatus().name(), "itemId", savedItem.getId())));

    notificationService.notify(
        lab.ownerAccountId(),
        NotificationType.MARKETPLACE,
        "Lịch hẹn ký gửi mới " + savedBooking.getBookingCode(),
        "Có lịch hẹn mang thiết bị " + savedItem.getItemName() + " tới kiểm định.",
        null);

    return consignmentMapper.toBookingResponse(savedBooking, savedItem);
  }

  /** UC46: Lab xác nhận người bán đã mang thiết bị tới quầy -> CHECKED_IN, AWAITING_INSPECTION. */
  @Override
  @Transactional
  public ConsignmentBookingResponse checkInBooking(
      Long bookingId, AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    ConsignmentBooking booking =
        bookingRepository
            .findById(bookingId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_BOOKING_NOT_FOUND,
                        "Không tìm thấy lịch hẹn ký gửi."));
    if (!Objects.equals(booking.getFilmLabId(), lab.getId())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_BOOKING_NOT_FOUND, "Không tìm thấy lịch hẹn ký gửi.");
    }
    if (booking.getStatus() != ConsignmentBookingStatus.BOOKED) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Chỉ được check-in cho lịch hẹn đang ở trạng thái BOOKED.");
    }

    ConsignmentItem item =
        itemRepository
            .findByBookingId(booking.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND,
                        "Không tìm thấy thiết bị của lịch hẹn."));

    Instant now = clock.instant();
    booking.setStatus(ConsignmentBookingStatus.CHECKED_IN);
    booking.setCheckedInAt(now);
    item.setStatus(ConsignmentItemStatus.AWAITING_INSPECTION);

    ConsignmentBooking savedBooking = bookingRepository.save(booking);
    ConsignmentItem savedItem = itemRepository.save(item);

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_BOOKING_CHECKED_IN,
            AuditTargetType.CONSIGNMENT_BOOKING,
            savedBooking.getId(),
            "Lab check-in lịch hẹn ký gửi " + savedBooking.getBookingCode(),
            Map.of("status", ConsignmentBookingStatus.BOOKED.name()),
            Map.of(
                "status", savedBooking.getStatus().name(),
                "itemStatus", savedItem.getStatus().name())));

    return consignmentMapper.toBookingResponse(savedBooking, savedItem);
  }

  /**
   * UC46, BR-47, BR-51: ghi biên bản kiểm định thiết bị. Khi ACCEPTED bắt buộc có hạng A–D, danh
   * sách hạng mục kiểm tra thuộc đúng loại thiết bị và từ 4 đến 20 ảnh kiểm định.
   */
  @Override
  @Transactional
  public ConditionReportResponse recordConditionReport(
      Long itemId,
      RecordConditionReportRequest request,
      List<MultipartFile> photos,
      AuthenticatedAccount ownerAccount) {
    FilmLab lab = labAccessService.requireOwnedLab(ownerAccount.id());
    ConsignmentItem item =
        itemRepository
            .findByIdWithBooking(itemId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi."));
    if (!Objects.equals(item.getBooking().getFilmLabId(), lab.getId())) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_ITEM_NOT_FOUND, "Không tìm thấy thiết bị ký gửi.");
    }
    if (item.getStatus() != ConsignmentItemStatus.AWAITING_INSPECTION
        || reportRepository.findByConsignmentItemId(itemId).isPresent()) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_STATUS_CONFLICT,
          "Thiết bị không ở trạng thái chờ kiểm định hoặc đã có biên bản kiểm định.");
    }

    validateConditionReportInput(item, request, photos);

    Instant now = clock.instant();
    ConditionReport report = new ConditionReport();
    report.setConsignmentItemId(item.getId());
    report.setConclusion(request.conclusion());
    report.setOverallGrade(
        request.conclusion() == ConditionConclusion.ACCEPTED ? request.overallGrade() : null);
    report.setDeclineReason(
        request.conclusion() == ConditionConclusion.DECLINED
            ? request.declineReason().strip()
            : null);
    report.setCorrectedItemName(blankToNull(request.correctedItemName()));
    report.setDiscrepancyNote(blankToNull(request.discrepancyNote()));
    report.setFilmExpiryDate(request.filmExpiryDate());
    report.setInspectorAccountId(ownerAccount.id());
    report.setInspectedAt(now);
    ConditionReport savedReport = reportRepository.save(report);

    final List<ConditionReportCheck> savedChecks =
        saveReportChecks(savedReport.getId(), request.checks());
    List<ConsignmentItemPhoto> savedPhotos =
        uploadInspectionPhotos(item.getId(), photos, ownerAccount.id());

    if (request.conclusion() == ConditionConclusion.DECLINED) {
      int collectionDays =
          intParam(SystemParameterKey.CONSIGNMENT_COLLECTION_DAYS, DEFAULT_COLLECTION_DAYS);
      item.setStatus(ConsignmentItemStatus.AWAITING_COLLECTION);
      item.setClosingReason(ConsignmentClosingReason.DECLINED_AT_INSPECTION);
      item.setCollectionDueAt(now.plus(Duration.ofDays(collectionDays)));
      itemRepository.save(item);
    }

    auditLogService.recordSuccess(
        new AuditEntry(
            AuditAction.CONSIGNMENT_CONDITION_REPORTED,
            AuditTargetType.CONDITION_REPORT,
            savedReport.getId(),
            "Lập biên bản kiểm định cho thiết bị #" + item.getId(),
            null,
            Map.of(
                "conclusion", savedReport.getConclusion().name(),
                "photoCount", savedPhotos.size())));

    notificationService.notify(
        item.getBooking().getSellerAccountId(),
        NotificationType.MARKETPLACE,
        "Đã có kết quả kiểm định cho " + item.getItemName(),
        "Kết luận kiểm định: " + savedReport.getConclusion().name() + ".",
        null);

    return consignmentMapper.toReportResponse(savedReport, item, savedChecks, savedPhotos);
  }

  private void validateConditionReportInput(
      ConsignmentItem item, RecordConditionReportRequest request, List<MultipartFile> photos) {
    List<MultipartFile> validPhotos =
        photos == null
            ? List.of()
            : photos.stream().filter(f -> f != null && !f.isEmpty()).toList();

    if (request.conclusion() == ConditionConclusion.ACCEPTED) {
      if (request.overallGrade() == null) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Biên bản ACCEPTED bắt buộc phải chọn hạng A, B, C hoặc D.");
      }
      if (request.declineReason() != null && !request.declineReason().isBlank()) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, "Biên bản ACCEPTED không được có lý do từ chối.");
      }
      if (request.checks() == null || request.checks().isEmpty()) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Biên bản ACCEPTED bắt buộc có danh sách hạng mục kiểm tra.");
      }
      if (validPhotos.size() < MIN_INSPECTION_PHOTOS
          || validPhotos.size() > MAX_INSPECTION_PHOTOS) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "Biên bản kiểm định bắt buộc đính kèm từ 4 đến 20 ảnh thực tế (BR-51).");
      }
    } else {
      if (request.declineReason() == null || request.declineReason().strip().length() < 20) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, "Lý do từ chối kiểm định phải dài từ 20 đến 500 ký tự.");
      }
      if (validPhotos.size() > MAX_INSPECTION_PHOTOS) {
        throw new BusinessException(
            ErrorCode.VALIDATION_ERROR, "Số lượng ảnh kiểm định tối đa là 20 ảnh.");
      }
    }

    if (request.checks() != null && !request.checks().isEmpty()) {
      Set<ConditionCheckCode> seen = EnumSet.noneOf(ConditionCheckCode.class);
      for (ConditionCheckItemRequest check : request.checks()) {
        if (!item.getItemType().supports(check.checkCode())) {
          throw new BusinessException(
              ErrorCode.CONSIGNMENT_CHECK_CODE_INVALID,
              "Mã kiểm định "
                  + check.checkCode()
                  + " không hợp lệ cho loại thiết bị "
                  + item.getItemType()
                  + ".");
        }
        if (!seen.add(check.checkCode())) {
          throw new BusinessException(
              ErrorCode.VALIDATION_ERROR, "Trùng mã hạng mục kiểm tra: " + check.checkCode());
        }
      }
    }
  }

  private List<ConditionReportCheck> saveReportChecks(
      Long reportId, List<ConditionCheckItemRequest> checkRequests) {
    if (checkRequests == null || checkRequests.isEmpty()) {
      return List.of();
    }
    List<ConditionReportCheck> entities = new ArrayList<>(checkRequests.size());
    for (ConditionCheckItemRequest req : checkRequests) {
      ConditionReportCheck entity = new ConditionReportCheck();
      entity.setId(new ConditionReportCheckId(reportId, req.checkCode()));
      entity.setResult(req.result());
      entity.setNote(blankToNull(req.note()));
      entities.add(entity);
    }
    return reportCheckRepository.saveAll(entities);
  }

  private List<ConsignmentItemPhoto> uploadInspectionPhotos(
      Long itemId, List<MultipartFile> photos, Long uploaderAccountId) {
    if (photos == null || photos.isEmpty()) {
      return List.of();
    }
    List<MultipartFile> nonEmpty =
        photos.stream().filter(f -> f != null && !f.isEmpty()).toList();
    List<ConsignmentItemPhoto> entities = new ArrayList<>(nonEmpty.size());
    short order = 1;
    for (MultipartFile file : nonEmpty) {
      String blobKey = uploadFile(file);
      ConsignmentItemPhoto photo = new ConsignmentItemPhoto();
      photo.setConsignmentItemId(itemId);
      photo.setSource(ConsignmentPhotoSource.INSPECTION);
      photo.setBlobKey(blobKey);
      photo.setDisplayOrder(order++);
      photo.setUploadedByAccountId(uploaderAccountId);
      entities.add(photo);
    }
    return itemPhotoRepository.saveAll(entities);
  }

  private String uploadFile(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return storageClient.upload(
          StoragePurpose.CONSIGNMENT_PHOTO, file.getOriginalFilename(), in, file.getSize());
    } catch (IOException ex) {
      throw new BusinessException(
          ErrorCode.STORAGE_UNAVAILABLE, "Không đọc được file ảnh kiểm định.");
    }
  }

  private void validateSlotAlignedWithLabWindow(
      Long labOwnerAccountId, Instant slotStartAt, short slotMinutes) {
    ZonedDateTime zonedStart = slotStartAt.atZone(VIETNAM_ZONE);
    LocalDate date = zonedStart.toLocalDate();
    OpeningWindow window = resolveOpeningWindow(labOwnerAccountId, date);
    if (window == null) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_SLOT_INVALID, "Film Lab đóng cửa vào ngày bạn chọn.");
    }

    LocalTime startLocal = zonedStart.toLocalTime();
    LocalTime endLocal = zonedStart.plusMinutes(slotMinutes).toLocalTime();
    long minutesFromOpen = Duration.between(window.opensAt(), startLocal).toMinutes();
    if (zonedStart.getSecond() != 0
        || zonedStart.getNano() != 0
        || startLocal.isBefore(window.opensAt())
        || endLocal.isAfter(window.closesAt())
        || minutesFromOpen % slotMinutes != 0) {
      throw new BusinessException(
          ErrorCode.CONSIGNMENT_SLOT_INVALID,
          "Khung giờ hẹn không nằm trong lịch nhận ký gửi của Film Lab.");
    }
  }

  private OpeningWindow resolveOpeningWindow(Long labOwnerAccountId, LocalDate date) {
    FilmLabProfileResponse profile = filmLabService.getMyProfile(labOwnerAccountId);
    List<FilmLabOpeningHourResponse> hours = profile.openingHours();
    if (hours == null || hours.isEmpty()) {
      return new OpeningWindow(DEFAULT_OPENS_AT, DEFAULT_CLOSES_AT);
    }
    int isoDay = date.getDayOfWeek().getValue();
    for (FilmLabOpeningHourResponse h : hours) {
      if (h.dayOfWeek() != null && h.dayOfWeek().intValue() == isoDay) {
        if (h.closed() || h.opensAt() == null || h.closesAt() == null) {
          return null;
        }
        return new OpeningWindow(h.opensAt(), h.closesAt());
      }
    }
    return new OpeningWindow(DEFAULT_OPENS_AT, DEFAULT_CLOSES_AT);
  }

  private ConsignmentLabSettings defaultUnsavedSettings(Long filmLabId) {
    ConsignmentLabSettings settings = new ConsignmentLabSettings();
    settings.setFilmLabId(filmLabId);
    settings.setAcceptingConsignments(false);
    settings.setSlotMinutes((short) 30);
    settings.setAppointmentsPerSlot((short) 1);
    return settings;
  }

  private String generateUniqueBookingCode() {
    for (int attempt = 0; attempt < 5; attempt++) {
      String code = "CBK" + randomChars(10);
      if (!bookingRepository.existsByBookingCode(code)) {
        return code;
      }
    }
    return "CBK" + randomChars(12);
  }

  private static String randomChars(int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    }
    return sb.toString();
  }

  private static String stripRequired(String raw, int min, int max, String label) {
    String stripped = raw == null ? "" : raw.strip();
    if (stripped.length() < min || stripped.length() > max) {
      throw new BusinessException(
          ErrorCode.VALIDATION_ERROR,
          label + " phải có độ dài từ " + min + " đến " + max + " ký tự.");
    }
    return stripped;
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  private int intParam(SystemParameterKey key, int fallback) {
    Integer val = systemParameterService.getInteger(key);
    return val != null ? val : fallback;
  }

  private record OpeningWindow(LocalTime opensAt, LocalTime closesAt) {}
}
