package com.jobtracker.dto;

/**
 * Structured result of classifying one email with Claude. `error` is set
 * (and everything else left at safe defaults) if the API call failed or
 * returned something we couldn't parse — the caller stores it for
 * debugging but doesn't act on it.
 */
public record EmailClassification(
        boolean isJobRelated,
        String company,
        String role,
        String status,
        float confidence,
        String error
) {
    public static EmailClassification failed(String error) {
        return new EmailClassification(false, null, null, null, 0f, error);
    }
}
