package com.jobtracker.job;

import com.jobtracker.dto.SyncSummary;
import com.jobtracker.model.GmailAccount;
import com.jobtracker.repository.GmailAccountRepository;
import com.jobtracker.service.EmailSyncOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * Polls every connected Gmail account on a fixed interval. Each account is
 * processed independently so one user's token/API failure doesn't block
 * the sync for everyone else.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailSyncScheduler {

    private final GmailAccountRepository gmailAccountRepository;
    private final EmailSyncOrchestratorService orchestratorService;

    @Scheduled(fixedDelayString = "${app.gmail.sync-interval-ms:300000}")
    public void syncAllAccounts() {
        List<GmailAccount> accounts = gmailAccountRepository.findAll();
        if (accounts.isEmpty()) {
            return;
        }

        log.info("Gmail sync starting for {} connected account(s)", accounts.size());
        for (GmailAccount account : accounts) {
            try {
                SyncSummary summary = orchestratorService.syncAccount(account);
                log.info("Gmail sync for user {} complete: {}", account.getUserId(), summary);
            } catch (Exception ex) {
                log.error("Gmail sync failed for user {}: {}", account.getUserId(), ex.getMessage(), ex);
            }
        }
    }
}
