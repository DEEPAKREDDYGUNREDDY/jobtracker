package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.EmailClassification;
import com.jobtracker.dto.FetchedEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends one email's subject/sender/snippet to the Claude API and asks it
 * to return strict JSON describing whether it's job-related and, if so,
 * the company/role/status it detects. This is the step that actually
 * populates the dashboard automatically.
 */
@Slf4j
@Service
public class ClaudeClassificationService {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String SYSTEM_PROMPT = """
            You classify emails for a job-application tracker. Given an email's
            sender, subject, and a short body snippet, decide whether it relates
            to a job application the user submitted, and extract structured data.

            Respond with ONLY a single JSON object, no prose, no markdown fences,
            matching exactly this shape:

            {
              "is_job_related": boolean,
              "company": string or null,
              "role": string or null,
              "status": one of "APPLIED", "SCREENING", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN", "ACCEPTED", or null,
              "confidence": number between 0 and 1
            }

            Guidance:
            - "APPLIED": application confirmation ("we received your application").
            - "SCREENING": recruiter reaching out, phone screen requests.
            - "INTERVIEW": interview scheduling/confirmation at any stage.
            - "OFFER": an offer letter or verbal offer email.
            - "REJECTED": rejection, "moving forward with other candidates", "keeping resume on file".
            - "WITHDRAWN": user withdrew their own application (rare in inbound mail).
            - "ACCEPTED": user's acceptance of an offer confirmed.
            - If the email is unrelated to a job search (newsletter, receipt, unrelated notification), set is_job_related to false and leave company/role/status null.
            - confidence should reflect how certain you are the company/status extraction is correct, not just whether it's job-related.
            - Extract the company name as it would naturally appear (e.g. "Acme Corp", not "Acme Corp Careers Team").
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ClaudeClassificationService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.anthropic.api-key:}") String apiKey,
            @Value("${app.anthropic.model:claude-haiku-4-5}") String model
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public EmailClassification classify(FetchedEmail email) {
        if (apiKey == null || apiKey.isBlank()) {
            return EmailClassification.failed("ANTHROPIC_API_KEY is not configured");
        }

        String userContent = """
                From: %s
                Subject: %s
                Snippet: %s
                """.formatted(
                nullToEmpty(email.from()),
                nullToEmpty(email.subject()),
                nullToEmpty(email.snippet())
        );

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 300,
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of("role", "user", "content", userContent))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            JsonNode response = restTemplate.postForObject(ENDPOINT, entity, JsonNode.class);

            if (response == null || !response.has("content") || response.get("content").isEmpty()) {
                return EmailClassification.failed("Empty response from Claude API");
            }

            String text = response.get("content").get(0).path("text").asText("");
            return parseClassification(text);
        } catch (Exception ex) {
            log.warn("Claude classification failed for message {}: {}", email.messageId(), ex.getMessage());
            return EmailClassification.failed(ex.getMessage());
        }
    }

    private EmailClassification parseClassification(String text) {
        try {
            String json = extractJsonObject(text);
            JsonNode node = objectMapper.readTree(json);

            boolean isJobRelated = node.path("is_job_related").asBoolean(false);
            String company = node.path("company").isNull() ? null : node.path("company").asText(null);
            String role = node.path("role").isNull() ? null : node.path("role").asText(null);
            String status = node.path("status").isNull() ? null : node.path("status").asText(null);
            float confidence = (float) node.path("confidence").asDouble(0.0);

            return new EmailClassification(isJobRelated, company, role, status, confidence, null);
        } catch (Exception ex) {
            log.warn("Could not parse Claude classification JSON: {}", text);
            return EmailClassification.failed("Unparseable classification response");
        }
    }

    /** Claude occasionally wraps JSON in prose or code fences despite instructions; strip to the outer braces. */
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalArgumentException("No JSON object found in response");
        }
        return text.substring(start, end + 1);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
