package com.fa26se088.filmlink.repository.account;

import com.fa26se088.filmlink.entity.account.LabRegistrationDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabRegistrationDocumentRepository
    extends JpaRepository<LabRegistrationDocument, Long> {

  List<LabRegistrationDocument> findByApplicationId(Long applicationId);
}

