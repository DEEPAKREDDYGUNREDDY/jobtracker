package com.jobtracker.dto;

public record SyncSummary(
        int fetched,
        int skippedAlreadyProcessed,
        int classified,
        int applicationsMatched,
        int applicationsCreated
) {
}
