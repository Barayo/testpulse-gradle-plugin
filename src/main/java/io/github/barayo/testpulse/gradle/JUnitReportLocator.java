package io.github.barayo.testpulse.gradle;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the Gradle JUnit XML report path for a given test class. */
public class JUnitReportLocator {

    public Path resolveReportPath(Path testResultsDir, Class<?> testClass) {
        return testResultsDir.resolve("TEST-" + testClass.getName() + ".xml");
    }

    /** @throws MissingReportException naming the expected path, if the report doesn't exist. */
    public void requireReportExists(Path testResultsDir, Class<?> testClass) throws MissingReportException {
        Path report = resolveReportPath(testResultsDir, testClass);
        if (!Files.isRegularFile(report)) {
            throw new MissingReportException(
                    "testpulse-gradle-plugin: expected test report at " + report
                            + " but it does not exist. This usually means the test was skipped "
                            + "(-x test) or a compile failure prevented the test task from running.");
        }
    }
}
