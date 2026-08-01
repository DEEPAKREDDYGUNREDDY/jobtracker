package com.jobtracker.service;

import com.jobtracker.dto.ApplicationRequest;
import com.jobtracker.dto.ApplicationResponse;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.model.ApplicationSource;
import com.jobtracker.model.ApplicationStatus;
import com.jobtracker.model.JobApplication;
import com.jobtracker.model.User;
import com.jobtracker.repository.JobApplicationRepository;
import com.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;




    public List<ApplicationResponse> listForUser(String userEmail, ApplicationStatus statusFilter) {
        System.out.println("🔴 CACHE MISS - Fetching from DB: " + userEmail + ":" + statusFilter);
        UUID userId = resolveUserId(userEmail);
        List<JobApplication> apps = (statusFilter == null)
                ? applicationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
                : applicationRepository.findAllByUserIdAndStatus(userId, statusFilter);
        return apps.stream().map(ApplicationResponse::fromEntity).toList();
    }



    public ApplicationResponse getOne(String userEmail, UUID applicationId) {
        UUID userId = resolveUserId(userEmail);
        JobApplication app = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return ApplicationResponse.fromEntity(app);
    }

    @Transactional
    public ApplicationResponse create(String userEmail, ApplicationRequest request) {
        UUID userId = resolveUserId(userEmail);

        JobApplication app = JobApplication.builder()
                .userId(userId)
                .companyName(request.companyName())
                .roleTitle(request.roleTitle())
                .status(request.status() != null ? request.status() : ApplicationStatus.APPLIED)
                .source(ApplicationSource.MANUAL)
                .jobUrl(request.jobUrl())
                .appliedDate(request.appliedDate())
                .notes(request.notes())
                .build();

        applicationRepository.save(app);
        return ApplicationResponse.fromEntity(app);
    }

    @Transactional
    public ApplicationResponse update(String userEmail, UUID applicationId, ApplicationRequest request) {
        UUID userId = resolveUserId(userEmail);

        JobApplication app = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        app.setCompanyName(request.companyName());
        app.setRoleTitle(request.roleTitle());
        if (request.status() != null) {
            app.setStatus(request.status());
        }
        app.setJobUrl(request.jobUrl());
        app.setAppliedDate(request.appliedDate());
        app.setNotes(request.notes());

        applicationRepository.save(app);
        return ApplicationResponse.fromEntity(app);
    }

    @Transactional
    public void delete(String userEmail, UUID applicationId) {
        UUID userId = resolveUserId(userEmail);

        if (!applicationRepository.existsByIdAndUserId(applicationId, userId)) {
            throw new ResourceNotFoundException("Application not found");
        }
        applicationRepository.deleteById(applicationId);
    }

    private UUID resolveUserId(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
