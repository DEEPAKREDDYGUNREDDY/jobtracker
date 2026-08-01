package com.jobtracker.controller;

import com.jobtracker.dto.GoogleTokenResponse;
import com.jobtracker.dto.SyncSummary;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.model.GmailAccount;
import com.jobtracker.model.User;
import com.jobtracker.repository.GmailAccountRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.security.OAuthStateService;
import com.jobtracker.service.EmailSyncOrchestratorService;
import com.jobtracker.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/gmail")
@RequiredArgsConstructor
public class GmailController {

    private final UserRepository userRepository;
    private final GmailAccountRepository gmailAccountRepository;
    private final GoogleOAuthService googleOAuthService;
    private final OAuthStateService oAuthStateService;
    private final EmailSyncOrchestratorService orchestratorService;

    /** Authenticated: returns the Google consent URL for the frontend to redirect the user to. */
    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(Authentication auth) {
        UUID userId = resolveUserId(auth.getName());
        String state = oAuthStateService.createState(userId);
        String authUrl = googleOAuthService.buildConsentUrl(state);
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * Public: Google redirects here after consent with no auth header of ours,
     * so the user is identified purely via the signed `state` param we issued.
     */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (error != null) {
            return htmlResponse(HttpStatus.BAD_REQUEST, "Gmail connection was not granted: " + error);
        }
        if (code == null) {
            return htmlResponse(HttpStatus.BAD_REQUEST, "Missing authorization code from Google");
        }

        UUID userId;
        try {
            userId = oAuthStateService.verifyAndExtractUserId(state);
        } catch (IllegalArgumentException ex) {
            return htmlResponse(HttpStatus.BAD_REQUEST, "Invalid or expired connection request: " + ex.getMessage());
        }

        GoogleTokenResponse tokens = googleOAuthService.exchangeCodeForTokens(code);
        if (tokens == null || tokens.access_token() == null) {
            return htmlResponse(HttpStatus.BAD_GATEWAY, "Google did not return an access token");
        }

        String googleEmail = safeFetchEmail(tokens.access_token());

        GmailAccount account = gmailAccountRepository.findByUserId(userId).orElseGet(() ->
                GmailAccount.builder().userId(userId).build());

        account.setGoogleEmail(googleEmail);
        account.setAccessToken(tokens.access_token());
        if (tokens.refresh_token() != null && !tokens.refresh_token().isBlank()) {
            account.setRefreshToken(tokens.refresh_token());
        } else if (account.getRefreshToken() == null) {
            return htmlResponse(HttpStatus.BAD_GATEWAY,
                    "Google did not return a refresh token. Revoke access at https://myaccount.google.com/permissions and try connecting again.");
        }
        account.setTokenExpiry(OffsetDateTime.now().plusSeconds(tokens.expires_in() != null ? tokens.expires_in() : 3600));

        account.setLastSyncedAt(OffsetDateTime.now().minusHours(3));
        gmailAccountRepository.save(account);

        return htmlResponse(HttpStatus.OK, "Gmail connected" + (googleEmail != null ? " (" + googleEmail + ")" : "")
                + ". You can close this tab and go back to the app.");
    }

    /** Authenticated: manually trigger a sync right now instead of waiting for the scheduler. */
    @PostMapping("/sync")
    public ResponseEntity<SyncSummary> sync(Authentication auth) {
        UUID userId = resolveUserId(auth.getName());
        GmailAccount account = gmailAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No Gmail account connected for this user"));

        SyncSummary summary = orchestratorService.syncAccount(account);
        return ResponseEntity.ok(summary);
    }

    /** Authenticated: connection status for the frontend to show a "connected" badge. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication auth) {
        UUID userId = resolveUserId(auth.getName());
        return gmailAccountRepository.findByUserId(userId)
                .map(a -> ResponseEntity.ok(Map.<String, Object>of(
                        "connected", true,
                        "googleEmail", a.getGoogleEmail() == null ? "" : a.getGoogleEmail(),
                        "lastSyncedAt", a.getLastSyncedAt() == null ? "" : a.getLastSyncedAt().toString()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("connected", false)));
    }

    private String safeFetchEmail(String accessToken) {
        try {
            return googleOAuthService.fetchGoogleEmail(accessToken);
        } catch (Exception ex) {
            log.warn("Could not fetch Google account email: {}", ex.getMessage());
            return null;
        }
    }

    private UUID resolveUserId(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }

    private ResponseEntity<String> htmlResponse(HttpStatus status, String message) {
        String html = "<html><body style=\"font-family: sans-serif; padding: 2rem;\"><p>" + message + "</p></body></html>";
        return ResponseEntity.status(status).body(html);
    }
}
