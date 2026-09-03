package io.github.barayo.testpulse.gradle;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiClassSubmissionFunctionalTest {

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
    void allTestClassesResultsAreSubmittedNotJustOne() throws IOException, Exception {
        // Reproduces a real QA finding: naive string concatenation of multiple
        // TEST-*.xml files produced an invalid multi-root document, and the backend's
        // encoding/xml-based parser silently decoded only the first one -- a real
        // multi-class project (the ordinary case) lost every class but one, while the
        // plugin logged a false "N/N matched" success.
        server.stubFor(post(urlEqualTo("/api/v1/projects/LOGIN/imports"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"RUN-1\"}")));

        FunctionalTestFixture.writeBuildFile(projectDir,
                "testpulse {\n"
                        + "    url = \"http://localhost:" + server.port() + "\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    token = \"t0k3n\"\n"
                        + "}\n");

        FunctionalTestFixture.writeTestSource(projectDir, "LoginTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class LoginTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\")\n"
                        + "    @Test\n"
                        + "    public void testLoginSucceeds() { }\n"
                        + "}\n");
        FunctionalTestFixture.writeTestSource(projectDir, "CheckoutTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class CheckoutTest {\n"
                        + "    @TestPulse(caseKey = \"CHECKOUT-1\")\n"
                        + "    @Test\n"
                        + "    public void testCheckoutSucceeds() { }\n"
                        + "}\n");
        FunctionalTestFixture.writeTestSource(projectDir, "SessionTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class SessionTest {\n"
                        + "    @TestPulse(caseKey = \"SESSION-1\")\n"
                        + "    @Test\n"
                        + "    public void testSessionSucceeds() { }\n"
                        + "}\n");

        BuildResult result = FunctionalTestFixture.run(projectDir, "test");
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());

        String requestBody = server.getAllServeEvents().get(0).getRequest().getBodyAsString();

        // The submitted "report" field must itself be one well-formed document
        // (single root, single prolog) containing ALL THREE classes' testcases --
        // not just the alphabetically-first one.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(requestBody);
        String report = json.get("report").asText();

        org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8)));
        org.w3c.dom.NodeList testsuites = doc.getElementsByTagName("testsuite");
        assertEquals(3, testsuites.getLength(), "expected all 3 test classes' <testsuite> elements, report was:\n" + report);
        assertTrue(report.contains("testpulse_case_key\" value=\"LOGIN-42\""), report);
        assertTrue(report.contains("testpulse_case_key\" value=\"CHECKOUT-1\""), report);
        assertTrue(report.contains("testpulse_case_key\" value=\"SESSION-1\""), report);
    }
}
