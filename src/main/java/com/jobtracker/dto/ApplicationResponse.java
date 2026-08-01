package com.jobtracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobtracker.model.JobApplication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID id;
    private String companyName;
    private String roleTitle;
    private String status;
    private String source;
    private String jobUrl;
    private LocalDate appliedDate;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ApplicationResponse fromEntity(JobApplication app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .companyName(app.getCompanyName())
                .roleTitle(app.getRoleTitle())
                .status(app.getStatus().name())
                .source(app.getSource().name())
                .jobUrl(app.getJobUrl())
                .appliedDate(app.getAppliedDate())
                .notes(app.getNotes())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}