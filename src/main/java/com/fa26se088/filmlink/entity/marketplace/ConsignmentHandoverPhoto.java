package com.fa26se088.filmlink.entity.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Ảnh bằng chứng tình trạng thiết bị lúc bàn giao cho người mua ({@code
 * consignment_handover_photos}).
 *
 * <p>Append-only: không có {@code updated_at} nên không kế thừa {@code BaseEntity}.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "consignment_handover_photos")
public class ConsignmentHandoverPhoto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "consignment_handover_id", nullable = false, updatable = false)
  private Long consignmentHandoverId;

  @Column(name = "blob_key", nullable = false, length = 500, updatable = false)
  private String blobKey;

  @Column(name = "display_order", nullable = false, updatable = false)
  private Short displayOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
