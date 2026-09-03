package io.github.barayo.testpulse.gradle.attachments;

import io.github.barayo.testpulse.gradle.AnnotatedCaseKeys;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Registers an attachment for a {@code @TestPulse}-annotated test. The
 * caller supplies its own case key explicitly -- {@code attach()}
 * deliberately does not try to auto-detect "the currently running test",
 * since that would need a JUnit-version-specific hook, breaking the
 * reflection-only architecture's JUnit 4/5 uniformity. Instead, it
 * identifies the calling class via {@link StackWalker} (no JUnit
 * dependency) and rejects the call unless {@code caseKey} is one of the
 * {@code @TestPulse} case keys declared on that class's own methods --
 * closing the cross-class/cross-module misattribution case TestPulse's
 * server-side contract cannot detect on its own.
 */
public final class TestPulseAttachments {
    private TestPulseAttachments() {
    }

    public static void attach(String caseKey, byte[] data, String filename, String contentType) throws IOException {
        if (!AttachmentStore.SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "testpulse-gradle-plugin: attach() only supports " + AttachmentStore.SUPPORTED_CONTENT_TYPES
                            + ", got: " + contentType);
        }

        Class<?> callingClass = findCallingClass();
        Set<String> allowedKeys = AnnotatedCaseKeys.forClass(callingClass);
        if (!allowedKeys.contains(caseKey)) {
            throw new IllegalArgumentException(
                    "testpulse-gradle-plugin: attach() called with case key \"" + caseKey
                            + "\", which is not declared via @TestPulse on " + callingClass.getName()
                            + ". Declared case keys on this class: " + allowedKeys);
        }

        AttachmentStore.write(scratchDir(), caseKey, data, filename, contentType);
    }

    private static Class<?> findCallingClass() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(c -> !c.equals(TestPulseAttachments.class))
                .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "testpulse-gradle-plugin: unable to determine the calling test class for attach()"));
    }

    private static Path scratchDir() {
        String configured = System.getProperty("testpulse.scratchDir");
        if (configured == null) {
            throw new IllegalStateException(
                    "testpulse-gradle-plugin: testpulse.scratchDir system property is not set -- "
                            + "TestPulseAttachments.attach() only works in a test run started by the "
                            + "io.github.barayo.testpulse Gradle plugin, which sets this automatically.");
        }
        return Paths.get(configured);
    }
}
