package io.github.barayo.testpulse.gradle.attachments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentStoreTest {

    @TempDir
    Path scratchDir;

    @Test
    void writesAndReadsBackAnAttachment() throws IOException {
        byte[] data = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

        AttachmentStore.write(scratchDir, "LOGIN-42", data, "failure.png", "image/png");

        List<AttachmentStore.StoredAttachment> all = AttachmentStore.readAll(scratchDir);
        assertEquals(1, all.size());
        assertEquals("LOGIN-42", all.get(0).caseKey);
        assertEquals("failure.png", all.get(0).filename);
        assertEquals("image/png", all.get(0).contentType);
    }

    @Test
    void rejectsUnsupportedContentType() {
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () ->
                AttachmentStore.write(scratchDir, "LOGIN-42", data, "x.pdf", "application/pdf"));

        assertEquals(0, AttachmentStore.readAll(scratchDir).size());
    }

    @Test
    void twoAttachmentsUnderTheSameCaseKeyBothSurvive() throws IOException {
        AttachmentStore.write(scratchDir, "LOGIN-42", "first".getBytes(StandardCharsets.UTF_8), "a.png", "image/png");
        AttachmentStore.write(scratchDir, "LOGIN-42", "second".getBytes(StandardCharsets.UTF_8), "b.png", "image/png");

        List<AttachmentStore.StoredAttachment> all = AttachmentStore.readAll(scratchDir);
        assertEquals(2, all.size());
        assertTrue(all.stream().allMatch(a -> a.caseKey.equals("LOGIN-42")));
        // distinct storage files -- not a single overwritten entry
        assertNotEquals(all.get(0).dataPath, all.get(1).dataPath);
    }
}
