package io.github.barayo.testpulse.gradle.attachments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persists attachments to a {@code build/testpulse/attachments/} scratch
 * directory (real disk I/O, since Gradle's Test task forks a separate
 * test-executor JVM process from the daemon process that later reads
 * these back). Sidecar filenames are a hash of {@code caseKey + a fresh
 * UUID per call} -- caseKey alone is deliberately NOT used, since
 * parameterized tests legitimately call attach() multiple times under
 * the same case key, and hashing caseKey alone would silently overwrite
 * prior attachments (the routine case here, not an edge case) and race
 * under Gradle's maxParallelForks &gt; 1.
 */
public final class AttachmentStore {
    public static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private AttachmentStore() {
    }

    public static final class StoredAttachment {
        public final String caseKey;
        public final String filename;
        public final String contentType;
        public final Path dataPath;

        StoredAttachment(String caseKey, String filename, String contentType, Path dataPath) {
            this.caseKey = caseKey;
            this.filename = filename;
            this.contentType = contentType;
            this.dataPath = dataPath;
        }
    }

    public static void write(Path scratchDir, String caseKey, byte[] data, String filename, String contentType) throws IOException {
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "testpulse-gradle-plugin: attach() only supports " + SUPPORTED_CONTENT_TYPES
                            + ", got: " + contentType);
        }

        Path dir = attachmentsDir(scratchDir);
        Files.createDirectories(dir);

        String hash = sha256Hex(caseKey + ":" + UUID.randomUUID());

        // Deliberately not JSON: this class runs inside forked test JVMs, whose
        // classpath is whatever the consuming project declares -- pulling in a JSON
        // library (Jackson, used elsewhere in this plugin's own daemon-side code)
        // would require separately propagating that dependency to every consumer's
        // test runtime, which a raw project.files(pluginJar) dependency doesn't do
        // (no transitive dependency metadata). Three fixed fields, one per line, needs
        // no library at all.
        List<String> meta = List.of(caseKey, filename, contentType);
        Files.write(dir.resolve(hash + ".meta"), meta, StandardCharsets.UTF_8);
        Files.write(dir.resolve(hash + ".data"), data);
    }

    public static List<StoredAttachment> readAll(Path scratchDir) {
        Path dir = attachmentsDir(scratchDir);
        List<StoredAttachment> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path meta : (Iterable<Path>) entries.filter(p -> p.toString().endsWith(".meta"))::iterator) {
                List<String> lines = Files.readAllLines(meta, StandardCharsets.UTF_8);
                String hash = meta.getFileName().toString().replace(".meta", "");
                Path dataPath = dir.resolve(hash + ".data");
                result.add(new StoredAttachment(lines.get(0), lines.get(1), lines.get(2), dataPath));
            }
        } catch (IOException e) {
            throw new RuntimeException("testpulse-gradle-plugin: failed to read attachments from " + dir, e);
        }
        return result;
    }

    private static Path attachmentsDir(Path scratchDir) {
        return scratchDir.resolve("attachments");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
