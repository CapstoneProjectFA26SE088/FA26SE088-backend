package com.fa26se088.filmlink.entity.account;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Giấy tờ pháp lý đính kèm trong đơn đăng ký lab (ERD #6, bảng {@code lab_registration_documents}).
 *
 * <p>Mỗi đơn có từ 1 đến 5 tài liệu định dạng PDF, JPG hoặc PNG (UC07).
 */
@Getter
@Setter
@Entity
@Table(name = "lab_registration_documents")
public class LabRegistrationDocument extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lab_registration_application_id", nullable = false)
  private LabRegistrationApplication application;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "blob_key", nullable = false, length = 500)
  private String blobKey;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes;
}

