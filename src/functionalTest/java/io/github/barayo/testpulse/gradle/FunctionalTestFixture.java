package io.github.barayo.testpulse.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared helper for building a small real Gradle project and running it via TestKit. */
final class FunctionalTestFixture {
    private FunctionalTestFixture() {
    }

    static final String BUILD_GRADLE_HEADER =
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
                    + "tasks.test { useJUnitPlatform() }\n";

    static void writeBuildFile(Path projectDir, String extraConfig) throws IOException {
        Files.writeString(projectDir.resolve("build.gradle.kts"), BUILD_GRADLE_HEADER + extraConfig, StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n", StandardCharsets.UTF_8);
    }

    static void writeTestSource(Path projectDir, String relativePath, String content) throws IOException {
        Path file = projectDir.resolve("src/test/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    static GradleRunner runner(Path projectDir, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    static BuildResult run(Path projectDir, String... arguments) {
        return runner(projectDir, arguments).build();
    }

    static BuildResult runExpectingFailure(Path projectDir, String... arguments) {
        return runner(projectDir, arguments).buildAndFail();
    }
}
