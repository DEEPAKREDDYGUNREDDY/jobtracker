package com.jobtracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a single inbound email that was scanned for job-application
 * signal. Populated by the (future) email-sync service; kept here now so
 * the schema and API don't need to change when that stage is built.
 */
@Entity
@Table(name = "email_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "email_message_id", nullable = false)
    private String emailMessageId;

    @Column(columnDefinition = "TEXT")
    private String subject;

    private String sender;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "detected_company")
    private String detectedCompany;

    @Column(name = "detected_status")
    private String detectedStatus;

    @Column(name = "raw_snippet", columnDefinition = "TEXT")
    private String rawSnippet;

    @Column(name = "is_job_related")
    private Boolean isJobRelated;

    private Float confidence;

    @Column(name = "classification_error", columnDefinition = "TEXT")
    private String classificationError;

    @Builder.Default
    private boolean processed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
