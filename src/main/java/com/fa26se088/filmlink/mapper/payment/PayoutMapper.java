package com.fa26se088.filmlink.mapper.payment;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.dto.payment.response.PayoutItemResponse;
import com.fa26se088.filmlink.dto.payment.response.PayoutResponse;
import com.fa26se088.filmlink.entity.payment.Payout;
import com.fa26se088.filmlink.entity.payment.PayoutItem;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Mapper cho {@link Payout} và {@link PayoutItem}. */
@Component
@RequiredArgsConstructor
public class PayoutMapper {

  private final StorageClient storageClient;

  public PayoutResponse toResponse(Payout entity) {
    return toResponse(entity, List.of());
  }

  public PayoutResponse toResponse(Payout entity, List<PayoutItem> items) {
    if (entity == null) {
      return null;
    }
    String receiptUrl = null;
    if (entity.getReceiptBlobKey() != null && !entity.getReceiptBlobKey().isBlank()) {
      URI signed = storageClient.signedUrl(entity.getReceiptBlobKey());
      receiptUrl = signed != null ? signed.toString() : null;
    }
    List<PayoutItemResponse> mappedItems =
        items == null
            ? List.of()
            : items.stream()
                .map(
                    item ->
                        new PayoutItemResponse(
                            item.getId(),
                            item.getServiceOrderId(),
                            item.getConsignmentSaleId(),
                            item.getAmountVnd()))
                .toList();
    return new PayoutResponse(
        entity.getId(),
        entity.getPayoutCode(),
        entity.getRecipientType(),
        entity.getFilmLabId(),
        entity.getRecipientAccountId(),
        entity.getAmountVnd(),
        entity.getStatus(),
        entity.getBankName(),
        entity.getBankAccountNumber(),
        entity.getAccountHolderName(),
        entity.getReceiptBlobKey(),
        receiptUrl,
        entity.getExecutedByAccountId(),
        entity.getPaidAt(),
        entity.getFailureReason(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        mappedItems);
  }
}
