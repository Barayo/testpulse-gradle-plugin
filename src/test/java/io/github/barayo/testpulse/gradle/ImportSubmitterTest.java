package io.github.barayo.testpulse.gradle;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.barayo.testpulse.gradle.attachments.AttachmentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class ImportSubmitterTest {

    private WireMockServer server;
    private String baseUrl;

    @TempDir
    Path scratchDir;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(0);
        server.start();
        baseUrl = "http://localhost:" + server.port();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void a201ResponseDoesNotFailTheBuildAndLogsASummary() {
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"RUN-1\"}")));

        ImportSubmitter.Result result = new ImportSubmitter().submit(
                baseUrl, "LOGIN", "t0k3n", "<testsuites></testsuites>", Collections.emptyList(), false, 1);

        assertFalse(result.buildShouldFail);
        assertTrue(result.logMessages.stream().anyMatch(m -> m.contains("RUN-1")));
    }

    @Test
    void a207ResponseWithDefaultConfigDoesNotFailAndLogsUnmatchedKeys() {
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(207).withHeader("Content-Type", "application/json")
                        .withBody("{\"run\":{\"key\":\"RUN-1\"},\"matched\":1,\"unmatched\":[{\"caseKey\":\"LOGIN-9\"}]}")));

        ImportSubmitter.Result result = new ImportSubmitter().submit(
                baseUrl, "LOGIN", "t0k3n", "<testsuites></testsuites>", Collections.emptyList(), false, 2);

        assertFalse(result.buildShouldFail);
        assertTrue(result.logMessages.stream().anyMatch(m -> m.contains("LOGIN-9")));
        assertTrue(result.logMessages.stream().anyMatch(m -> m.contains("failOnUnmatched")));
    }

    @Test
    void a207ResponseWithFailOnUnmatchedFailsTheBuild() {
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(207).withHeader("Content-Type", "application/json")
                        .withBody("{\"run\":{\"key\":\"RUN-1\"},\"matched\":1,\"unmatched\":[{\"caseKey\":\"LOGIN-9\"}]}")));

        ImportSubmitter.Result result = new ImportSubmitter().submit(
                baseUrl, "LOGIN", "t0k3n", "<testsuites></testsuites>", Collections.emptyList(), true, 2);

        assertTrue(result.buildShouldFail);
    }

    @Test
    void aSubmissionErrorAlwaysFailsTheBuildRegardlessOfFailOnUnmatched() {
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"unauthorized\"}")));

        ImportSubmitter.Result result = new ImportSubmitter().submit(
                baseUrl, "LOGIN", "bad-token", "<testsuites></testsuites>", Collections.emptyList(), false, 1);

        assertTrue(result.buildShouldFail);
    }

    @Test
    void aNetworkErrorAlwaysFailsTheBuild() {
        server.stop(); // nothing listening -- connection refused
        ImportSubmitter.Result result = new ImportSubmitter().submit(
                "http://localhost:1", "LOGIN", "t0k3n", "<testsuites></testsuites>", Collections.emptyList(), false, 1);

        assertTrue(result.buildShouldFail);
    }

    @Test
    void allAttachmentsSharingACaseKeyAreIncludedInTheRequestNotJustTheLast() throws IOException {
        AttachmentStore.write(scratchDir, "LOGIN-42", "first".getBytes(), "a.png", "image/png");
        AttachmentStore.write(scratchDir, "LOGIN-42", "second".getBytes(), "b.png", "image/png");
        List<AttachmentStore.StoredAttachment> attachments = AttachmentStore.readAll(scratchDir);

        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"RUN-1\"}")));

        new ImportSubmitter().submit(baseUrl, "LOGIN", "t0k3n", "<testsuites></testsuites>", attachments, false, 1);

        server.verify(postRequestedFor(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .withRequestBody(matchingJsonPath("$.attachments[?(@.filename == 'a.png')]"))
                .withRequestBody(matchingJsonPath("$.attachments[?(@.filename == 'b.png')]")));
    }

    @Test
    void tokenIsNeverPresentInAnyLogMessageOnSuccessOrFailure() {
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"unauthorized\"}")));

        ImportSubmitter.Result result = new ImportSubmitter().submit(
                baseUrl, "LOGIN", "super-secret-token-value", "<testsuites></testsuites>", Collections.emptyList(), false, 1);

        for (String message : result.logMessages) {
            assertFalse(message.contains("super-secret-token-value"), "log message leaked the token: " + message);
        }
    }

    @Test
    void dryRunFetchesCasesAndDoesNotSubmit() {
        server.stubFor(get(urlEqualTo("/api/v1/projects/LOGIN/cases"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"key\":\"LOGIN-42\"}]")));

        Set<String> annotated = new TreeSet<>(Set.of("LOGIN-42", "LOGIN-99"));
        ImportSubmitter.DryRunResult result = new ImportSubmitter().dryRun(baseUrl, "LOGIN", "t0k3n", annotated);

        assertTrue(result.logMessages.stream().anyMatch(m -> m.contains("LOGIN-99")));
        server.verify(0, postRequestedFor(urlPathMatching(".*")));
    }
}
