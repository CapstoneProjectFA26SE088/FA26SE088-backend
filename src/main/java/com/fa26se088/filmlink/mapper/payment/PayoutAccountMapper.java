package com.fa26se088.filmlink.mapper.payment;

import com.fa26se088.filmlink.dto.payment.response.PayoutAccountResponse;
import com.fa26se088.filmlink.entity.payment.PayoutAccount;
import org.springframework.stereotype.Component;

/** Mapper cho {@link PayoutAccount}. */
@Component
public class PayoutAccountMapper {

  public PayoutAccountResponse toResponse(PayoutAccount entity) {
    return new PayoutAccountResponse(
        entity.getId(),
        entity.getFilmLabId(),
        entity.getBankName(),
        entity.getBankAccountNumber(),
        entity.getAccountHolderName(),
        entity.getUpdatedAt());
  }
}
