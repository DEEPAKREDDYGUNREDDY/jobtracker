package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.dto.FetchedEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GmailFetchService {

    private static final String LIST_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final String GET_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages/{id}";
    private static final int MAX_RESULTS_PER_SYNC = 25;

    private final RestTemplate restTemplate;

    public GmailFetchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Lists message ids that arrived after the given cursor. Gmail's search
     * query language accepts "after:<epoch seconds>", which is the cheapest
     * way to bound the poll without tracking historyId cursors.
     */
    public List<String> listNewMessageIds(String accessToken, OffsetDateTime since) {
        long afterEpochSeconds = since.toEpochSecond();
        String query = "after:" + afterEpochSeconds + " -in:chats -in:spam -in:trash";

        String url = UriComponentsBuilder.fromHttpUrl(LIST_ENDPOINT)
                .queryParam("q", query)
                .queryParam("maxResults", MAX_RESULTS_PER_SYNC)
                .build()
                .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(accessToken));
        JsonNode response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class).getBody();

        List<String> ids = new ArrayList<>();
        if (response != null && response.has("messages")) {
            for (JsonNode m : response.get("messages")) {
                ids.add(m.get("id").asText());
            }
        }
        return ids;
    }

    public FetchedEmail fetchMessage(String accessToken, String messageId) {
        String url = UriComponentsBuilder.fromHttpUrl(GET_ENDPOINT)
                .queryParam("format", "metadata")
                .queryParam("metadataHeaders", "Subject")
                .queryParam("metadataHeaders", "From")
                .buildAndExpand(messageId)
                .toUriString();

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(accessToken));
        JsonNode message = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class).getBody();

        if (message == null) {
            return null;
        }

        String subject = null;
        String from = null;
        JsonNode headers = message.path("payload").path("headers");
        for (JsonNode h : headers) {
            String name = h.path("name").asText("");
            if (name.equalsIgnoreCase("Subject")) {
                subject = h.path("value").asText(null);
            } else if (name.equalsIgnoreCase("From")) {
                from = h.path("value").asText(null);
            }
        }

        String snippet = message.path("snippet").asText(null);

        OffsetDateTime receivedAt = null;
        if (message.hasNonNull("internalDate")) {
            long millis = message.get("internalDate").asLong();
            receivedAt = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
        }

        return new FetchedEmail(messageId, subject, from, snippet, receivedAt);
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
