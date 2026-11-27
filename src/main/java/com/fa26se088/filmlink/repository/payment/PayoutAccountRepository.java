package com.fa26se088.filmlink.repository.payment;

import com.fa26se088.filmlink.entity.payment.PayoutAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository cho {@link PayoutAccount}. */
@Repository
public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, Long> {

  Optional<PayoutAccount> findByFilmLabId(Long filmLabId);

  Optional<PayoutAccount> findByUserAccountId(Long userAccountId);
}
