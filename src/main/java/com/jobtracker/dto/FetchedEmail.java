package com.jobtracker.dto;

import java.time.OffsetDateTime;

/**
 * Trimmed-down view of a Gmail message: just enough to pre-filter and
 * hand to Claude for classification. We deliberately don't parse the full
 * MIME body (base64 multipart parsing) for this MVP — subject + snippet
 * carries most of the classification signal for ATS emails.
 */
public record FetchedEmail(
        String messageId,
        String subject,
        String from,
        String snippet,
        OffsetDateTime receivedAt
) {
}
