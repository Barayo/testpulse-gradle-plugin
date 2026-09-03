package io.github.barayo.testpulse.gradle.attachments;

import io.github.barayo.testpulse.TestPulse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPulseAttachmentsTest {

    @TempDir
    Path scratchDir;

    @BeforeEach
    void setUp() {
        System.setProperty("testpulse.scratchDir", scratchDir.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("testpulse.scratchDir");
    }

    // Calling attach() directly from a test method here would make
    // TestPulseAttachmentsTest itself the "calling class" StackWalker sees, and it has
    // no @TestPulse annotations -- so each test instead invokes through
    // SampleTestInvoker's own @TestPulse-annotated method, so the calling class really
    // is a class that declares the case key(s) under test.

    @Test
    void acceptsAnAttachmentMatchingTheCallingClasssOwnCaseKey() throws Exception {
        byte[] data = "bytes".getBytes(StandardCharsets.UTF_8);
        SampleTestInvoker.attachAsSampleTest("LOGIN-42", data, "failure.png", "image/png");

        List<AttachmentStore.StoredAttachment> stored = AttachmentStore.readAll(scratchDir);
        assertEquals(1, stored.size());
        assertEquals("LOGIN-42", stored.get(0).caseKey);
    }

    @Test
    void rejectsAnAttachmentUnderACaseKeyBelongingToADifferentClass() {
        byte[] data = "bytes".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () ->
                SampleTestInvoker.attachAsSampleTest("SOME-OTHER-CASE-NOT-ON-SAMPLE-TEST", data, "x.png", "image/png"));

        assertEquals(0, AttachmentStore.readAll(scratchDir).size());
    }

    @Test
    void rejectsUnsupportedContentTypeBeforeTheAllowlistCheckWouldOtherwiseAccept() {
        byte[] data = "bytes".getBytes(StandardCharsets.UTF_8);

        // LOGIN-42 IS a valid case key for SampleTest -- content-type rejection must still win.
        assertThrows(IllegalArgumentException.class, () ->
                SampleTestInvoker.attachAsSampleTest("LOGIN-42", data, "x.pdf", "application/pdf"));

        assertEquals(0, AttachmentStore.readAll(scratchDir).size());
    }

    /** A real class carrying @TestPulse annotations, whose own method calls attach() -- so StackWalker sees THIS class as the caller, not the test class. */
    static class SampleTestInvoker {
        @TestPulse(caseKey = "LOGIN-42")
        static void attachAsSampleTest(String caseKey, byte[] data, String filename, String contentType) throws Exception {
            TestPulseAttachments.attach(caseKey, data, filename, contentType);
        }
    }
}
