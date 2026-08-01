package com.jobtracker.repository;

import com.jobtracker.model.ApplicationStatus;
import com.jobtracker.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<JobApplication> findAllByUserIdAndStatus(UUID userId, ApplicationStatus status);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    Optional<JobApplication> findFirstByUserIdAndCompanyNameIgnoreCase(UUID userId, String companyName);
}
