package com.jobtracker.service;

import com.jobtracker.dto.EmailClassification;
import com.jobtracker.dto.FetchedEmail;
import com.jobtracker.dto.GoogleTokenResponse;
import com.jobtracker.dto.SyncSummary;
import com.jobtracker.model.ApplicationSource;
import com.jobtracker.model.ApplicationStatus;
import com.jobtracker.model.EmailEvent;
import com.jobtracker.model.GmailAccount;
import com.jobtracker.model.JobApplication;
import com.jobtracker.repository.EmailEventRepository;
import com.jobtracker.repository.GmailAccountRepository;
import com.jobtracker.repository.JobApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Ties the whole email-sync pipeline together for one connected Gmail
 * account: refresh token if needed -> list new messages -> pre-filter ->
 * classify with Claude -> match/create a JobApplication -> log an
 * EmailEvent for every message so nothing gets reprocessed twice.
 */
@Slf4j
@Service
public class EmailSyncOrchestratorService {

    private final GmailAccountRepository gmailAccountRepository;
    private final GoogleOAuthService googleOAuthService;
    private final GmailFetchService gmailFetchService;
    private final EmailPreFilterService preFilterService;
    private final ClaudeClassificationService classificationService;
    private final EmailEventRepository emailEventRepository;
    private final JobApplicationRepository applicationRepository;
    private final float confidenceThreshold;
    private final int initialLookbackDays;

    public EmailSyncOrchestratorService(
            GmailAccountRepository gmailAccountRepository,
            GoogleOAuthService googleOAuthService,
            GmailFetchService gmailFetchService,
            EmailPreFilterService preFilterService,
            ClaudeClassificationService classificationService,
            EmailEventRepository emailEventRepository,
            JobApplicationRepository applicationRepository,
            @Value("${app.gmail.confidence-threshold:0.6}") float confidenceThreshold,
            @Value("${app.gmail.initial-lookback-days:30}") int initialLookbackDays
    ) {
        this.gmailAccountRepository = gmailAccountRepository;
        this.googleOAuthService = googleOAuthService;
        this.gmailFetchService = gmailFetchService;
        this.preFilterService = preFilterService;
        this.classificationService = classificationService;
        this.emailEventRepository = emailEventRepository;
        this.applicationRepository = applicationRepository;
        this.confidenceThreshold = confidenceThreshold;
        this.initialLookbackDays = initialLookbackDays;
    }

    @Transactional
    public SyncSummary syncAccount(GmailAccount account) {
        String accessToken = ensureFreshAccessToken(account);

        OffsetDateTime since = account.getLastSyncedAt() != null
                ? account.getLastSyncedAt()
                : OffsetDateTime.now().minusDays(initialLookbackDays);

        List<String> messageIds = gmailFetchService.listNewMessageIds(accessToken, since);

        int fetched = 0, skipped = 0, classified = 0, matched = 0, created = 0;

        for (String messageId : messageIds) {
            if (emailEventRepository.existsByUserIdAndEmailMessageId(account.getUserId(), messageId)) {
                skipped++;
                continue;
            }

            FetchedEmail email = gmailFetchService.fetchMessage(accessToken, messageId);
            if (email == null) {
                continue;
            }
            fetched++;

            EmailEvent.EmailEventBuilder eventBuilder = EmailEvent.builder()
                    .userId(account.getUserId())
                    .emailMessageId(messageId)
                    .subject(email.subject())
                    .sender(email.from())
                    .receivedAt(email.receivedAt())
                    .rawSnippet(email.snippet())
                    .processed(true);

            if (!preFilterService.isWorthClassifying(email)) {
                eventBuilder.isJobRelated(false);
                emailEventRepository.save(eventBuilder.build());
                continue;
            }

            EmailClassification result = classificationService.classify(email);
            classified++;

            eventBuilder
                    .isJobRelated(result.isJobRelated())
                    .confidence(result.confidence())
                    .detectedCompany(result.company())
                    .detectedStatus(result.status())
                    .classificationError(result.error());

            boolean actedOn = result.error() == null
                    && result.isJobRelated()
                    && result.confidence() >= confidenceThreshold
                    && result.company() != null && !result.company().isBlank();

            if (actedOn) {
                MatchResult match = matchOrCreateApplication(account.getUserId(), email, result);
                eventBuilder.applicationId(match.application().getId());
                if (match.created()) {
                    created++;
                } else {
                    matched++;
                }
            }

            emailEventRepository.save(eventBuilder.build());
        }

        account.setLastSyncedAt(OffsetDateTime.now());
        gmailAccountRepository.save(account);

        return new SyncSummary(fetched, skipped, classified, matched, created);
    }

    private record MatchResult(JobApplication application, boolean created) {
    }

    private MatchResult matchOrCreateApplication(java.util.UUID userId, FetchedEmail email, EmailClassification result) {
        Optional<JobApplication> existing =
                applicationRepository.findFirstByUserIdAndCompanyNameIgnoreCase(userId, result.company());

        ApplicationStatus status = parseStatus(result.status());

        if (existing.isPresent()) {
            JobApplication app = existing.get();
            if (status != null) {
                app.setStatus(status);
            }
            applicationRepository.save(app);
            return new MatchResult(app, false);
        }

        JobApplication app = JobApplication.builder()
                .userId(userId)
                .companyName(result.company())
                .roleTitle(result.role() != null && !result.role().isBlank() ? result.role() : "Unknown role")
                .status(status != null ? status : ApplicationStatus.APPLIED)
                .source(ApplicationSource.EMAIL)
                .appliedDate(email.receivedAt() != null ? email.receivedAt().toLocalDate() : null)
                .notes("Auto-created from email: " + trim(email.subject()))
                .build();

        applicationRepository.save(app);
        return new MatchResult(app, true);
    }

    private ApplicationStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return ApplicationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String ensureFreshAccessToken(GmailAccount account) {
        if (account.getTokenExpiry() != null && account.getTokenExpiry().isAfter(OffsetDateTime.now().plusMinutes(1))) {
            return account.getAccessToken();
        }

        GoogleTokenResponse refreshed = googleOAuthService.refreshAccessToken(account.getRefreshToken());
        account.setAccessToken(refreshed.access_token());
        account.setTokenExpiry(OffsetDateTime.now().plusSeconds(
                refreshed.expires_in() != null ? refreshed.expires_in() : 3600));
        gmailAccountRepository.save(account);
        return account.getAccessToken();
    }

    private String trim(String s) {
        if (s == null) return "";
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
