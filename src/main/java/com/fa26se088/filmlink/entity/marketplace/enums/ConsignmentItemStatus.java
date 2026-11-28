package com.fa26se088.filmlink.entity.marketplace.enums;

/** Trạng thái vòng đời món đồ ký gửi ({@code ck_consignment_items_status}). */
public enum ConsignmentItemStatus {
  AWAITING_DROP_OFF,
  AWAITING_INSPECTION,
  AWAITING_SELLER_CONFIRMATION,
  NEGOTIATING,
  READY_TO_LIST,
  LISTED,
  SOLD,
  AWAITING_COLLECTION,
  COLLECTED,
  CANCELLED,
  LOST_OR_DAMAGED
}
