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

class FailingTestStillSubmitsFunctionalTest {

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
    void aGenuinelyFailingTestTaskStillRunsAnnotateAndSubmit() throws IOException {
        // Reproduces a real QA finding: dependsOn(testTask) previously caused Gradle to
        // skip testpulseAnnotate/testpulseSubmit entirely whenever `test` itself failed
        // -- the one situation results matter most, silently reporting nothing.
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"RUN-1\"}")));

        FunctionalTestFixture.writeBuildFile(projectDir,
                "testpulse {\n"
                        + "    url = \"http://localhost:" + server.port() + "\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    token = \"t0k3n\"\n"
                        + "}\n");

        FunctionalTestFixture.writeTestSource(projectDir, "FailingTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "import static org.junit.jupiter.api.Assertions.fail;\n"
                        + "public class FailingTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\")\n"
                        + "    @Test\n"
                        + "    public void testGenuinelyFails() { fail(\"a real regression\"); }\n"
                        + "}\n");

        // The `test` task itself fails -- expect the overall build to fail too (a real
        // test failure should still fail the build), but testpulseAnnotate/Submit must
        // still have run and actually submitted the (failed) result.
        BuildResult result = FunctionalTestFixture.runExpectingFailure(projectDir, "test");

        assertTrue(result.getOutput().contains(":testpulseAnnotate"), result.getOutput());
        assertTrue(result.getOutput().contains(":testpulseSubmit"), result.getOutput());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .withRequestBody(containing("testpulse_case_key"))
                .withRequestBody(containing("LOGIN-42")));
    }
}
