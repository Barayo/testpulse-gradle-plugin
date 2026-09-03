package io.github.barayo.testpulse.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterizedAttachmentFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void multipleAttachCallsUnderTheSameCaseKeySurviveAcrossParameterizedInvocations() throws IOException {
        FunctionalTestFixture.writeBuildFile(projectDir,
                "dependencies {\n"
                        + "    testImplementation(\"org.junit.jupiter:junit-jupiter-params\")\n"
                        + "}\n"
                        + "testpulse {\n"
                        + "    url = \"http://localhost:1\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    dryRun = true\n"
                        + "}\n");

        FunctionalTestFixture.writeTestSource(projectDir, "ParameterizedAttachTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import io.github.barayo.testpulse.gradle.attachments.TestPulseAttachments;\n"
                        + "import org.junit.jupiter.params.ParameterizedTest;\n"
                        + "import org.junit.jupiter.params.provider.ValueSource;\n"
                        + "public class ParameterizedAttachTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\")\n"
                        + "    @ParameterizedTest\n"
                        + "    @ValueSource(ints = {1, 2, 3})\n"
                        + "    public void testWithMultipleInvocations(int value) throws Exception {\n"
                        + "        TestPulseAttachments.attach(\"LOGIN-42\", new byte[]{(byte) value}, \"v\" + value + \".png\", \"image/png\");\n"
                        + "    }\n"
                        + "}\n");

        BuildResult result = FunctionalTestFixture.run(projectDir, "test");
        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());

        Path attachmentsDir = projectDir.resolve("build/testpulse/attachments");
        assertTrue(Files.isDirectory(attachmentsDir), "expected " + attachmentsDir + " to exist");
        List<Path> metaFiles;
        try (Stream<Path> entries = Files.list(attachmentsDir)) {
            metaFiles = entries.filter(p -> p.toString().endsWith(".meta")).collect(Collectors.toList());
        }
        // Three parameterized invocations, each calling attach() once -- all three must
        // survive as distinct entries, not overwrite each other.
        assertEquals(3, metaFiles.size(), "expected 3 distinct attachments, found: " + metaFiles);
    }
}
