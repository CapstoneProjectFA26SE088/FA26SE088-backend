package com.fa26se088.filmlink.entity.marketplace.enums;

import java.util.EnumSet;
import java.util.Set;

/** Loại thiết bị ký gửi ({@code ck_consignment_items_type}) và tập mã kiểm định hợp lệ. */
public enum ConsignmentItemType {
  CAMERA_BODY(
      EnumSet.of(
          ConditionCheckCode.SHUTTER,
          ConditionCheckCode.LIGHT_METERING,
          ConditionCheckCode.MIRROR,
          ConditionCheckCode.BATTERY_COMPARTMENT,
          ConditionCheckCode.LIGHT_SEALS,
          ConditionCheckCode.FRAME_COUNTER)),
  LENS(
      EnumSet.of(
          ConditionCheckCode.FUNGUS,
          ConditionCheckCode.SCRATCHES,
          ConditionCheckCode.HAZE,
          ConditionCheckCode.APERTURE,
          ConditionCheckCode.FOCUS_RING,
          ConditionCheckCode.MOUNT)),
  FILM(
      EnumSet.of(
          ConditionCheckCode.EXPIRY_DATE,
          ConditionCheckCode.STORAGE_CONDITIONS,
          ConditionCheckCode.FACTORY_SEAL));

  private final Set<ConditionCheckCode> allowedCheckCodes;

  ConsignmentItemType(Set<ConditionCheckCode> allowedCheckCodes) {
    this.allowedCheckCodes = Set.copyOf(allowedCheckCodes);
  }

  /** Kiểm tra mã hạng mục có thuộc loại thiết bị này không (UC46). */
  public boolean supports(ConditionCheckCode code) {
    return code != null && allowedCheckCodes.contains(code);
  }

  /** Trả về tập mã kiểm định hợp lệ cho loại thiết bị này. */
  public Set<ConditionCheckCode> allowedCheckCodes() {
    return allowedCheckCodes;
  }
}
