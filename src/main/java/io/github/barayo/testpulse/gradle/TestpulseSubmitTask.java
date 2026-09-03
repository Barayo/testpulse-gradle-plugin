package io.github.barayo.testpulse.gradle;

import io.github.barayo.testpulse.gradle.attachments.AttachmentStore;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads the annotated JUnit report(s) plus any registered attachments and
 * submits them to TestPulse's import API, applying the 201/207/error
 * exit-code policy already shipped in the other three plugins. Mirrors
 * testpulse-maven-plugin's SubmitMojo, with an attachments array and a
 * dry-run mode added.
 */
public class TestpulseSubmitTask extends DefaultTask {
    private final DirectoryProperty testResultsDir = getProject().getObjects().directoryProperty();
    private final RegularFileProperty annotatedCaseKeysFile = getProject().getObjects().fileProperty();
    private final DirectoryProperty scratchDir = getProject().getObjects().directoryProperty();
    private final Property<TestPulseExtension> extension = getProject().getObjects().property(TestPulseExtension.class);

    @InputFiles
    public DirectoryProperty getTestResultsDir() {
        return testResultsDir;
    }

    @InputFile
    public RegularFileProperty getAnnotatedCaseKeysFile() {
        return annotatedCaseKeysFile;
    }

    @Internal
    public DirectoryProperty getScratchDir() {
        return scratchDir;
    }

    @Internal
    public Property<TestPulseExtension> getExtension() {
        return extension;
    }

    @TaskAction
    public void submit() throws Exception {
        Set<String> annotatedCaseKeys = readAnnotatedCaseKeys();
        if (annotatedCaseKeys.isEmpty()) {
            // No @TestPulse-annotated tests anywhere: no configuration required, no request made.
            return;
        }

        ConfigResolver resolver = new ConfigResolver(
                name -> (String) getProject().findProperty(name), System::getenv, extension.get());

        String url = requireSetting(resolver.resolveUrl(), "url", "testpulse.url", null);
        String projectKey = requireSetting(resolver.resolveProject(), "project", "testpulse.project", null);
        boolean dryRun = resolver.resolveDryRun();

        ImportSubmitter submitter = new ImportSubmitter();

        if (dryRun) {
            String token = resolver.resolveToken();
            ImportSubmitter.DryRunResult result = submitter.dryRun(url, projectKey, token, annotatedCaseKeys);
            for (String message : result.logMessages) {
                getLogger().lifecycle(message);
            }
            return;
        }

        String token = requireSetting(resolver.resolveToken(), "token", "testpulse.token", "TESTPULSE_TOKEN");
        boolean failOnUnmatched = resolver.resolveFailOnUnmatched();

        String combinedReport = readCombinedReport();
        List<AttachmentStore.StoredAttachment> attachments = AttachmentStore.readAll(scratchDir.get().getAsFile().toPath());

        ImportSubmitter.Result result = submitter.submit(
                url, projectKey, token, combinedReport, attachments, failOnUnmatched, annotatedCaseKeys.size());

        for (String message : result.logMessages) {
            getLogger().lifecycle(message);
        }
        if (result.buildShouldFail) {
            throw new GradleException(String.join("\n", result.logMessages));
        }
    }

    private Set<String> readAnnotatedCaseKeys() throws Exception {
        Path file = annotatedCaseKeysFile.get().getAsFile().toPath();
        if (!Files.isRegularFile(file)) {
            return new LinkedHashSet<>();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                keys.add(line);
            }
        }
        return keys;
    }

    private String readCombinedReport() throws Exception {
        File dir = testResultsDir.get().getAsFile();
        if (!dir.isDirectory()) {
            return "";
        }
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> reports = Files.list(dir.toPath())) {
            for (Path report : (Iterable<Path>) reports.filter(p -> p.toString().endsWith(".xml"))::iterator) {
                combined.append(Files.readString(report, StandardCharsets.UTF_8));
            }
        }
        return combined.toString();
    }

    private String requireSetting(String value, String name, String property, String envVar) {
        if (value == null || value.isEmpty()) {
            String envClause = envVar != null ? "the " + envVar + " environment variable, or " : "";
            throw new GradleException(
                    "testpulse-gradle-plugin: missing required setting '" + name + "'. Set it via "
                            + "-P" + property + "=..., " + envClause + "the testpulse { } extension block.");
        }
        return value;
    }
}
