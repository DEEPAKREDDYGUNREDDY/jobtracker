package com.jobtracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores the Gmail OAuth tokens for a connected user, plus a sync cursor
 * so each scheduled poll only fetches mail that arrived after the last run.
 */
@Entity
@Table(name = "gmail_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmailAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "google_email")
    private String googleEmail;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry", nullable = false)
    private OffsetDateTime tokenExpiry;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "connected_at", updatable = false)
    private OffsetDateTime connectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
