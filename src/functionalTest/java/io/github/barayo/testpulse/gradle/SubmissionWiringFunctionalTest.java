package io.github.barayo.testpulse.gradle;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionWiringFunctionalTest {

    @TempDir
    Path projectDir;

    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void aPlainGradlewTestRunsAnnotateAndSubmitAutomaticallyViaFinalizedBy() throws IOException {
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"RUN-1\"}")));

        FunctionalTestFixture.writeBuildFile(projectDir,
                "testpulse {\n"
                        + "    url = \"http://localhost:" + server.port() + "\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    token = \"t0k3n\"\n"
                        + "}\n");

        FunctionalTestFixture.writeTestSource(projectDir, "SampleTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class SampleTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\")\n"
                        + "    @Test\n"
                        + "    public void testLoginSucceeds() { }\n"
                        + "}\n");

        // Only "test" is requested -- testpulseAnnotate/testpulseSubmit are never named
        // explicitly, proving the finalizedBy wiring alone drives the whole pipeline.
        BuildResult result = FunctionalTestFixture.run(projectDir, "test");

        assertTrue(result.getOutput().contains(":testpulseAnnotate"), result.getOutput());
        assertTrue(result.getOutput().contains(":testpulseSubmit"), result.getOutput());
        assertTrue(result.getOutput().contains("RUN-1"), result.getOutput());

        server.verify(postRequestedFor(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .withHeader("Authorization", equalTo("Bearer t0k3n"))
                .withRequestBody(matchingJsonPath("$.report"))
                .withRequestBody(containing("testpulse_case_key")));
    }
}
