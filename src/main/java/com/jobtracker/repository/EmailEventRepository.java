package com.jobtracker.repository;

import com.jobtracker.model.EmailEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailEventRepository extends JpaRepository<EmailEvent, UUID> {
    Optional<EmailEvent> findByUserIdAndEmailMessageId(UUID userId, String emailMessageId);
    boolean existsByUserIdAndEmailMessageId(UUID userId, String emailMessageId);
}
