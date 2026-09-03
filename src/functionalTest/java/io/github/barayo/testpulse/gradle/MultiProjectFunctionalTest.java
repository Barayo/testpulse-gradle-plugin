package io.github.barayo.testpulse.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProjectFunctionalTest {

    @TempDir
    Path projectDir;

    private void writeModuleBuild(Path moduleDir, String caseKey) throws IOException {
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("build.gradle.kts"),
                "plugins {\n"
                        + "    id(\"java\")\n"
                        + "    id(\"io.github.barayo.testpulse\")\n"
                        + "}\n"
                        + "repositories { mavenCentral() }\n"
                        + "dependencies {\n"
                        + "    testImplementation(platform(\"org.junit:junit-bom:5.10.3\"))\n"
                        + "    testImplementation(\"org.junit.jupiter:junit-jupiter\")\n"
                        + "    testRuntimeOnly(\"org.junit.platform:junit-platform-launcher\")\n"
                        + "}\n"
                        + "tasks.test { useJUnitPlatform() }\n"
                        + "testpulse {\n"
                        + "    url = \"http://localhost:1\"\n"
                        + "    project = \"LOGIN\"\n"
                        + "    dryRun = true\n"
                        + "}\n", StandardCharsets.UTF_8);

        Path testFile = moduleDir.resolve("src/test/java/SampleTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile,
                "import io.github.barayo.testpulse.TestPulse;\n"
                        + "import org.junit.jupiter.api.Test;\n"
                        + "public class SampleTest {\n"
                        + "    @TestPulse(caseKey = \"" + caseKey + "\")\n"
                        + "    @Test\n"
                        + "    public void testSomething() { }\n"
                        + "}\n", StandardCharsets.UTF_8);
    }

    @Test
    void eachSubprojectReadsAndWritesOnlyItsOwnBuildDirectory() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"multi\"\ninclude(\"module-a\", \"module-b\")\n", StandardCharsets.UTF_8);

        writeModuleBuild(projectDir.resolve("module-a"), "MODA-1");
        writeModuleBuild(projectDir.resolve("module-b"), "MODB-1");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("test")
                .forwardOutput()
                .build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());

        Path reportA = projectDir.resolve("module-a/build/test-results/test/TEST-SampleTest.xml");
        Path reportB = projectDir.resolve("module-b/build/test-results/test/TEST-SampleTest.xml");
        assertTrue(Files.isRegularFile(reportA), "expected " + reportA);
        assertTrue(Files.isRegularFile(reportB), "expected " + reportB);

        String xmlA = Files.readString(reportA);
        String xmlB = Files.readString(reportB);
        assertTrue(xmlA.contains("MODA-1"), xmlA);
        assertTrue(!xmlA.contains("MODB-1"), "module-a's report must not contain module-b's case key: " + xmlA);
        assertTrue(xmlB.contains("MODB-1"), xmlB);
        assertTrue(!xmlB.contains("MODA-1"), "module-b's report must not contain module-a's case key: " + xmlB);
    }
}
