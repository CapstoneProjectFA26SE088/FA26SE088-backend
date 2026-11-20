package com.fa26se088.filmlink.entity.delivery;

import com.fa26se088.filmlink.dto.delivery.request.ShipmentRequest;
import com.fa26se088.filmlink.dto.delivery.response.ShipmentResult;
import com.fa26se088.filmlink.entity.delivery.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "shipments")
@Data
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã đơn nội bộ. UNIQUE để chống tạo trùng vận đơn cho cùng một đơn. */
    @Column(nullable = false, unique = true, name = "order_code")
    private String orderCode;

    /** Mã provider: "ghn", "grab", "mock"... */
    @Column(nullable = false, name = "provider")
    private String provider;

    /** Mã vận đơn bên nhà vận chuyển, dùng để đối chiếu webhook. */
    @Column(name = "provider_order_id", nullable = false)
    private String providerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "status")
    private DeliveryStatus status;

    @Column(name = "cod_amount_vnd", nullable = false)
    private long codAmount;   // tiền thu hộ (VND)
    @Column(name = "estimated_fee_vnd")
    private long fee;         // phí vận chuyển (VND)

    /** Thời điểm của event gần nhất đã áp dụng, dùng để bỏ qua event cũ đến muộn. */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Shipment() { } // bắt buộc cho JPA

    public static Shipment of(ShipmentRequest req, String provider, ShipmentResult res) {
        Shipment s = new Shipment();
        s.orderCode = req.orderCode();
        s.provider = provider;
        s.providerOrderId = res.providerOrderId();
        s.status = res.status();
        s.codAmount = req.codAmount();
        s.fee = res.fee();
        return s;
    }

    /**
     * Áp dụng trạng thái mới nếu hợp lệ.
     * @return true nếu có thay đổi; false nếu bị bỏ qua (sai thứ tự / event cũ / đã kết thúc)
     */
    public boolean applyStatus(DeliveryStatus next, Instant at) {
        if (!status.canTransitionTo(next)) return false;
        if (at != null && lastEventAt != null && at.isBefore(lastEventAt)) return false;
        this.status = next;
        this.lastEventAt = at;
        return true;
    }

}
