package com.fa26se088.filmlink.repository.account;

import com.fa26se088.filmlink.entity.account.LabRegistrationApplication;
import com.fa26se088.filmlink.entity.account.enums.LabApplicationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabRegistrationApplicationRepository
    extends JpaRepository<LabRegistrationApplication, Long> {

  Optional<LabRegistrationApplication> findByApplicationCode(String applicationCode);

  boolean existsByApplicationCode(String applicationCode);

  boolean existsByTaxCode(String taxCode);

  boolean existsByAddressLineIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(
      String addressLine, String district, String province);

  Page<LabRegistrationApplication> findByStatus(LabApplicationStatus status, Pageable pageable);
}

