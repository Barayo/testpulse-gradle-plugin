package io.github.barayo.testpulse.gradle;

/** A test has {@code @TestPulse} annotations but Gradle never produced its report. */
public class MissingReportException extends Exception {
    public MissingReportException(String message) {
        super(message);
    }
}
