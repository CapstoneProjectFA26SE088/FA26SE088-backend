package com.fa26se088.filmlink.entity.account;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import com.fa26se088.filmlink.entity.masterdata.FilmFormat;
import com.fa26se088.filmlink.entity.masterdata.FilmProcess;
import com.fa26se088.filmlink.entity.masterdata.ServiceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Đơn đăng ký mở Film Lab (ERD #5, bảng {@code lab_registration_applications}).
 *
 * <p>Được nộp bởi Guest (UC07) và xét duyệt bởi Admin (UC08).
 */
@Getter
@Setter
@Entity
@Table(name = "lab_registration_applications")
public class LabRegistrationApplication extends BaseEntity {

  @Column(name = "application_code", nullable = false, unique = true, length = 20)
  private String applicationCode;

  @Column(name = "lab_name", nullable = false, length = 150)
  private String labName;

  @Column(name = "tax_code", nullable = false, length = 14)
  private String taxCode;

  @Column(name = "address_line", nullable = false, length = 255)
  private String addressLine;

  @Column(name = "district", nullable = false, length = 100)
  private String district;

  @Column(name = "province", nullable = false, length = 100)
  private String province;

  @Column(name = "latitude", precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(name = "longitude", precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(name = "contact_email", nullable = false, length = 255)
  private String contactEmail;

  @Column(name = "contact_phone", nullable = false, length = 15)
  private String contactPhone;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LabApplicationStatus status = LabApplicationStatus.PENDING_REVIEW;

  @Column(name = "is_duplicate_suspected", nullable = false)
  private boolean duplicateSuspected = false;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "reviewed_by_account_id")
  private Long reviewedByAccountId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "info_request_note", length = 500)
  private String infoRequestNote;

  @Column(name = "info_requested_at")
  private Instant infoRequestedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;

  @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<LabRegistrationDocument> documents = new ArrayList<>();

  @ManyToMany
  @JoinTable(
      name = "lab_registration_application_service_types",
      joinColumns = @JoinColumn(name = "lab_registration_application_id"),
      inverseJoinColumns = @JoinColumn(name = "service_type_id"))
  private Set<ServiceType> serviceTypes = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "lab_registration_application_film_processes",
      joinColumns = @JoinColumn(name = "lab_registration_application_id"),
      inverseJoinColumns = @JoinColumn(name = "film_process_id"))
  private Set<FilmProcess> filmProcesses = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "lab_registration_application_film_formats",
      joinColumns = @JoinColumn(name = "lab_registration_application_id"),
      inverseJoinColumns = @JoinColumn(name = "film_format_id"))
  private Set<FilmFormat> filmFormats = new HashSet<>();

  /**
   * Thêm một tài liệu pháp lý vào đơn.
   */
  public void addDocument(LabRegistrationDocument document) {
    documents.add(document);
    document.setApplication(this);
  }
}

