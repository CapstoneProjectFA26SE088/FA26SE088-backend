package com.fa26se088.filmlink.entity.marketplace;

import com.fa26se088.filmlink.entity.BaseEntity;
import com.fa26se088.filmlink.entity.marketplace.enums.ConsignmentPhotoSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ảnh chụp thiết bị ký gửi của người bán hoặc ảnh kiểm định của Lab
 * ({@code consignment_item_photos}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consignment_item_photos")
public class ConsignmentItemPhoto extends BaseEntity {

  @Column(name = "consignment_item_id", nullable = false)
  private Long consignmentItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private ConsignmentPhotoSource source;

  @Column(name = "blob_key", nullable = false, length = 500)
  private String blobKey;

  @Column(name = "display_order", nullable = false)
  private Short displayOrder;

  @Column(name = "uploaded_by_account_id", nullable = false)
  private Long uploadedByAccountId;
}
