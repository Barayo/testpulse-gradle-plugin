package io.github.barayo.testpulse.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.barayo.testpulse.gradle.attachments.AttachmentStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Submits an annotated JUnit report (plus any attachments) to TestPulse's
 * import API and maps the response to a build outcome. Ported from
 * testpulse-maven-plugin's ImportSubmitter (no Maven-specific
 * dependencies there either), extended with an attachments array and a
 * dry-run mode.
 */
public class ImportSubmitter {

    public static final class Result {
        public final boolean buildShouldFail;
        public final List<String> logMessages;

        Result(boolean buildShouldFail, List<String> logMessages) {
            this.buildShouldFail = buildShouldFail;
            this.logMessages = logMessages;
        }
    }

    public static final class DryRunResult {
        public final List<String> logMessages;

        DryRunResult(List<String> logMessages) {
            this.logMessages = logMessages;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Result submit(String baseUrl, String projectKey, String token, String report,
                          List<AttachmentStore.StoredAttachment> attachments,
                          boolean failOnUnmatched, int total) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("format", "junit-xml");
        payload.put("report", report);
        payload.set("attachments", buildAttachmentsArray(attachments));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(baseUrl) + "/api/v1/projects/" + projectKey + "/imports"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
        } catch (Exception e) {
            return new Result(true, List.of("testpulse-gradle-plugin: failed to build submission request: " + e));
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            return new Result(true, List.of("testpulse-gradle-plugin: submission failed: " + e));
        }

        return handleResponse(response, failOnUnmatched, total);
    }

    public DryRunResult dryRun(String baseUrl, String projectKey, String token, Set<String> annotatedCaseKeys) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stripTrailingSlash(baseUrl) + "/api/v1/projects/" + projectKey + "/cases"))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            return new DryRunResult(List.of("testpulse-gradle-plugin: dry run failed to fetch cases: " + e));
        }

        if (response.statusCode() >= 300) {
            return new DryRunResult(List.of(
                    "testpulse-gradle-plugin: dry run failed to fetch cases (status " + response.statusCode() + ")"));
        }

        Set<String> existingKeys = new TreeSet<>();
        JsonNode body = parseJson(response.body());
        if (body != null && body.isArray()) {
            for (JsonNode entry : body) {
                if (entry.has("key")) {
                    existingKeys.add(entry.get("key").asText());
                }
            }
        }

        Set<String> unmatched = new TreeSet<>();
        int matched = 0;
        for (String key : annotatedCaseKeys) {
            if (existingKeys.contains(key)) {
                matched++;
            } else {
                unmatched.add(key);
            }
        }

        String message = "testpulse-gradle-plugin: dry run -- " + matched + " would match, " + unmatched.size()
                + " would not" + (unmatched.isEmpty() ? "" : ": " + String.join(", ", unmatched));
        return new DryRunResult(List.of(message));
    }

    private ArrayNode buildAttachmentsArray(List<AttachmentStore.StoredAttachment> attachments) {
        ArrayNode array = mapper.createArrayNode();
        for (AttachmentStore.StoredAttachment attachment : attachments) {
            try {
                ObjectNode node = mapper.createObjectNode();
                node.put("caseKey", attachment.caseKey);
                node.put("filename", attachment.filename);
                node.put("contentType", attachment.contentType);
                node.put("data", Base64.getEncoder().encodeToString(Files.readAllBytes(attachment.dataPath)));
                array.add(node);
            } catch (IOException e) {
                throw new RuntimeException("testpulse-gradle-plugin: failed to read attachment data from "
                        + attachment.dataPath, e);
            }
        }
        return array;
    }

    private Result handleResponse(HttpResponse<String> response, boolean failOnUnmatched, int total) {
        int status = response.statusCode();
        List<String> messages = new ArrayList<>();

        if (status == 201) {
            JsonNode run = parseJson(response.body());
            String runKey = runIdentifier(run);
            messages.add("testpulse-gradle-plugin: " + total + "/" + total + " matched, run " + runKey + " created");
            return new Result(false, messages);
        }

        if (status == 207) {
            JsonNode body = parseJson(response.body());
            JsonNode run = body != null ? body.get("run") : null;
            String runKey = runIdentifier(run);
            int matched = body != null && body.has("matched") ? body.get("matched").asInt() : 0;

            TreeSet<String> unmatchedKeys = new TreeSet<>();
            if (body != null && body.has("unmatched")) {
                for (JsonNode entry : body.get("unmatched")) {
                    if (entry.has("caseKey")) {
                        unmatchedKeys.add(entry.get("caseKey").asText());
                    }
                }
            }

            String hint = failOnUnmatched ? "" : " Re-run with failOnUnmatched to make this a hard failure.";
            messages.add("testpulse-gradle-plugin: " + matched + "/" + total + " matched, run " + runKey
                    + " created. Unmatched: " + String.join(", ", unmatchedKeys) + "." + hint);

            return new Result(failOnUnmatched, messages);
        }

        messages.add("testpulse-gradle-plugin: submission rejected (" + status + "): " + response.body());
        return new Result(true, messages);
    }

    private String runIdentifier(JsonNode run) {
        if (run == null) {
            return "?";
        }
        if (run.has("key")) {
            return run.get("key").asText();
        }
        if (run.has("id")) {
            return run.get("id").asText();
        }
        return "?";
    }

    private JsonNode parseJson(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
