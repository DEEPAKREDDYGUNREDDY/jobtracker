package com.jobtracker.repository;

import com.jobtracker.model.GmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GmailAccountRepository extends JpaRepository<GmailAccount, UUID> {
    Optional<GmailAccount> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
