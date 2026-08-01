package com.jobtracker.service;

import com.jobtracker.dto.FetchedEmail;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Cheap heuristic filter that runs before we spend a Claude API call.
 * Goal: reject obvious non-job-application mail (newsletters, promos,
 * social notifications) without paying for classification, while staying
 * loose enough not to silently drop real ATS emails. When in doubt, let
 * it through — a false positive here just costs a fraction of a cent.
 */
@Service
public class EmailPreFilterService {

    private static final List<String> BLOCKED_SENDER_FRAGMENTS = List.of(
            "noreply@facebookmail", "noreply@twitter", "no-reply@linkedin.com",
            "notifications@github.com", "newsletter", "@substack.com",
            "no-reply@medium.com", "@promotions.", "digest@"
    );

    private static final List<String> JOB_KEYWORDS = List.of(
            "application", "applied", "position", "role", "candidate",
            "interview", "recruiter", "recruiting", "hiring", "offer",
            "job", "career", "resume", "cv", "screening", "assessment",
            "onboarding", "background check", "next steps", "thank you for your interest"
    );

    private static final List<String> KNOWN_ATS_SENDER_FRAGMENTS = List.of(
            "greenhouse.io", "lever.co", "myworkday.com", "icims.com",
            "smartrecruiters.com", "ashbyhq.com", "taleo.net", "successfactors",
            "workable.com", "bamboohr.com", "jobvite.com"
    );

    public boolean isWorthClassifying(FetchedEmail email) {
        String from = lower(email.from());
        String subject = lower(email.subject());
        String snippet = lower(email.snippet());

        if (BLOCKED_SENDER_FRAGMENTS.stream().anyMatch(from::contains)) {
            return false;
        }

        if (KNOWN_ATS_SENDER_FRAGMENTS.stream().anyMatch(from::contains)) {
            return true;
        }

        String haystack = subject + " " + snippet;
        return JOB_KEYWORDS.stream().anyMatch(haystack::contains);
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
