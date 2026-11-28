package com.fa26se088.filmlink.entity.marketplace.enums;

/** Lý do đóng hoặc hoàn trả món đồ ký gửi ({@code ck_consignment_items_closing}). */
public enum ConsignmentClosingReason {
  DECLINED_AT_INSPECTION,
  GRADE_DISPUTED,
  NEGOTIATION_FAILED,
  WITHDRAWN_BY_SELLER,
  EXPIRED_NO_RESPONSE,
  TAKEN_BACK_AT_EXPIRY,
  REMOVED_BY_MODERATOR,
  BOOKING_MISSED,
  RETURNED_BY_BUYER,
  LOST_OR_DAMAGED_AT_LAB
}
