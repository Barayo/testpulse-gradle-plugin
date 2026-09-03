package io.github.barayo.testpulse.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentAllowlistFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void realBuildFailsWhenATestAttachesUnderACaseKeyBelongingToADifferentClass() throws IOException {
        FunctionalTestFixture.writeBuildFile(projectDir,
                "testpulse {\n"
                        + "    url = \"http://localhost:1\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    dryRun = true\n"
                        + "}\n");

        // OtherClassTest declares LOGIN-99 -- MisattributingTest tries to attach() under
        // that key from a class that doesn't declare it at all.
        FunctionalTestFixture.writeTestSource(projectDir, "OtherClassTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class OtherClassTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-99\")\n"
                        + "    @Test\n"
                        + "    public void testSomething() { }\n"
                        + "}\n");

        FunctionalTestFixture.writeTestSource(projectDir, "MisattributingTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import io.github.barayo.testpulse.gradle.attachments.TestPulseAttachments;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class MisattributingTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\")\n"
                        + "    @Test\n"
                        + "    public void testFailsWithBadPassword() throws Exception {\n"
                        + "        TestPulseAttachments.attach(\"LOGIN-99\", new byte[]{1,2,3}, \"x.png\", \"image/png\");\n"
                        + "    }\n"
                        + "}\n");

        BuildResult result = FunctionalTestFixture.runExpectingFailure(projectDir, "test");

        assertTrue(result.getOutput().contains("BUILD FAILED"), result.getOutput());
        assertTrue(result.getOutput().contains("MisattributingTest"), result.getOutput());

        // The exception's own message (naming the rejected case key and the calling
        // class) lands in the test's own XML report, not Gradle's console summary.
        Path reportPath = projectDir.resolve("build/test-results/test/TEST-MisattributingTest.xml");
        assertTrue(Files.isRegularFile(reportPath), "expected " + reportPath);
        String report = Files.readString(reportPath);
        assertTrue(report.contains("LOGIN-99"), report);
        assertTrue(report.contains("not declared via @TestPulse on MisattributingTest"), report);
    }
}
