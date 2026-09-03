package io.github.barayo.testpulse.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JUnitReportLocatorTest {

    static class SampleTest {
    }

    @TempDir
    Path reportsDir;

    @Test
    void resolvesTheExpectedReportPath() {
        Path expected = reportsDir.resolve("TEST-" + SampleTest.class.getName() + ".xml");

        Path actual = new JUnitReportLocator().resolveReportPath(reportsDir, SampleTest.class);

        assertEquals(expected, actual);
    }

    @Test
    void throwsNamingTheExpectedPathWhenReportIsMissing() {
        MissingReportException ex = assertThrows(MissingReportException.class,
                () -> new JUnitReportLocator().requireReportExists(reportsDir, SampleTest.class));

        Path expected = reportsDir.resolve("TEST-" + SampleTest.class.getName() + ".xml");
        assertTrue(ex.getMessage().contains(expected.toString()));
    }

    @Test
    void doesNotThrowWhenReportExists() throws IOException, MissingReportException {
        Path expected = reportsDir.resolve("TEST-" + SampleTest.class.getName() + ".xml");
        Files.writeString(expected, "<testsuite></testsuite>");

        new JUnitReportLocator().requireReportExists(reportsDir, SampleTest.class);
        // no exception == pass
    }
}
