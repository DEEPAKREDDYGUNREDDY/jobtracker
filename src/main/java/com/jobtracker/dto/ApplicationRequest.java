package com.jobtracker.dto;

import com.jobtracker.model.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ApplicationRequest(
        @NotBlank String companyName,
        @NotBlank String roleTitle,
        ApplicationStatus status,
        String jobUrl,
        LocalDate appliedDate,
        String notes
) {}
