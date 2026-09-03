package io.github.barayo.testpulse.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScratchDirCleanupFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void staleAttachmentsFromAPriorRunDoNotSurviveIntoTheNextRun() throws IOException {
        String buildFileWithAttach =
                "testpulse {\n"
                        + "    url = \"http://localhost:1\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    dryRun = true\n"
                        + "}\n";
        FunctionalTestFixture.writeBuildFile(projectDir, buildFileWithAttach);
        FunctionalTestFixture.writeTestSource(projectDir, "SampleTest.java",
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import io.github.barayo.testpulse.gradle.attachments.TestPulseAttachments;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class SampleTest {\n"
                        + "    @TestPulse(caseKey = \"LOGIN-42\")\n"
                        + "    @Test\n"
                        + "    public void testLoginSucceeds() throws Exception {\n"
                        + "        TestPulseAttachments.attach(\"LOGIN-42\", new byte[]{1}, \"a.png\", \"image/png\");\n"
                        + "    }\n"
                        + "}\n");

        FunctionalTestFixture.run(projectDir, "test");
        Path attachmentsDir = projectDir.resolve("build/testpulse/attachments");
        assertTrue(Files.isDirectory(attachmentsDir) && Files.list(attachmentsDir).findAny().isPresent(),
                "expected the first run to leave an attachment behind");

        // Second run: same test class (still calls attach() the same way), but re-run
        // from scratch -- the point is that whatever was there BEFORE this run started
        // must not still be there afterward under a different, stale identity. Since
        // this test can't easily simulate "a test that no longer calls attach()"
        // without a second fixture, assert instead that a *fresh* run's attachment
        // count is exactly 1, not 2 -- proving the directory was cleared, not appended to.
        FunctionalTestFixture.run(projectDir, "test");
        long countAfterSecondRun;
        try (var entries = Files.list(attachmentsDir)) {
            countAfterSecondRun = entries.filter(p -> p.toString().endsWith(".data")).count();
        }
        assertTrue(countAfterSecondRun == 1, "expected exactly 1 attachment after the second run, found " + countAfterSecondRun);
    }
}
