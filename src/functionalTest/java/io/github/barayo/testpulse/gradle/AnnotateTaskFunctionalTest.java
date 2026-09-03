package io.github.barayo.testpulse.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotateTaskFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void realGradleBuildInjectsTestpulsePropertiesIntoTheJunitReport() throws IOException {
        // dryRun=true against an unreachable URL means testpulseSubmit's finalizer never
        // fails the build (dry run never fails on a fetch error) -- isolating this test
        // to what testpulseAnnotate itself produced.
        FunctionalTestFixture.writeBuildFile(projectDir,
                "testpulse {\n"
                        + "    url = \"http://localhost:1\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    dryRun = true\n"
                        + "}\n");

        FunctionalTestFixture.writeTestSource(projectDir, "SampleTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class SampleTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\", platform = \"linux\", tags = {\"smoke\"})\n"
                        + "    @Test\n"
                        + "    public void testLoginSucceeds() { }\n"
                        + "}\n");

        BuildResult result = FunctionalTestFixture.run(projectDir, "test");

        Path reportPath = projectDir.resolve("build/test-results/test/TEST-SampleTest.xml");
        assertTrue(Files.isRegularFile(reportPath), "expected report at " + reportPath);
        String xml = Files.readString(reportPath);
        assertTrue(xml.contains("<property name=\"testpulse_case_key\" value=\"LOGIN-42\"/>"), xml);
        assertTrue(xml.contains("<property name=\"testpulse_platform\" value=\"linux\"/>"), xml);
        assertTrue(xml.contains("<property name=\"testpulse_tags\" value=\"smoke\"/>"), xml);
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
    }
}
