package com.fa26se088.filmlink.entity.payment;

import com.fa26se088.filmlink.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tài khoản ngân hàng nhận tiền hiện hành của lab hoặc người bán ký gửi (UC35, UC64), bảng {@code
 * payout_accounts}. Mỗi chủ sở hữu có tối đa một tài khoản ({@code uq_payout_accounts_lab}, {@code
 * uq_payout_accounts_user}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payout_accounts")
public class PayoutAccount extends BaseEntity {

  @Column(name = "film_lab_id")
  private Long filmLabId;

  @Column(name = "user_account_id")
  private Long userAccountId;

  @Column(name = "bank_name", nullable = false, length = 100)
  private String bankName;

  @Column(name = "bank_account_number", nullable = false, length = 30)
  private String bankAccountNumber;

  @Column(name = "account_holder_name", nullable = false, length = 100)
  private String accountHolderName;
}
