package io.github.barayo.testpulse.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads compiled test classes, matches {@code @TestPulse}-annotated
 * methods to the corresponding {@code TEST-*.xml} testcases by reflection,
 * and rewrites those reports in place. Ported mechanism from
 * testpulse-maven-plugin's AnnotateMojo, adapted to Gradle's task API.
 */
public class TestpulseAnnotateTask extends DefaultTask {
    private final ConfigurableFileCollection testClassesDirs = getProject().files();
    private final ConfigurableFileCollection testRuntimeClasspath = getProject().files();
    private final DirectoryProperty testResultsDir = getProject().getObjects().directoryProperty();
    private final RegularFileProperty annotatedCaseKeysFile = getProject().getObjects().fileProperty();

    @InputFiles
    public ConfigurableFileCollection getTestClassesDirs() {
        return testClassesDirs;
    }

    @Classpath
    public ConfigurableFileCollection getTestRuntimeClasspath() {
        return testRuntimeClasspath;
    }

    @InputFiles
    public DirectoryProperty getTestResultsDir() {
        return testResultsDir;
    }

    @OutputFile
    public RegularFileProperty getAnnotatedCaseKeysFile() {
        return annotatedCaseKeysFile;
    }

    @TaskAction
    public void annotate() throws Exception {
        List<String> classNames = collectClassNames();
        Set<String> annotatedCaseKeys = new LinkedHashSet<>();

        if (classNames.isEmpty()) {
            writeAnnotatedCaseKeysFile(annotatedCaseKeys);
            return;
        }

        ClassLoader classLoader = buildTestClassLoader();
        TestClassLoader.LoadResult loadResult = new TestClassLoader().loadAll(classNames, classLoader);
        for (TestClassLoader.FailedClass failed : loadResult.failed) {
            getLogger().warn("testpulse-gradle-plugin: skipping {} (failed to load: {})", failed.className, failed.cause);
        }

        Path testResultsPath = testResultsDir.get().getAsFile().toPath();
        JUnitReportLocator locator = new JUnitReportLocator();
        JUnitReportAnnotator annotator = new JUnitReportAnnotator();

        for (Class<?> clazz : loadResult.loaded) {
            Set<String> classCaseKeys = AnnotatedCaseKeys.forClass(clazz);
            if (classCaseKeys.isEmpty()) {
                continue;
            }
            annotatedCaseKeys.addAll(classCaseKeys);

            locator.requireReportExists(testResultsPath, clazz);
            Path reportPath = locator.resolveReportPath(testResultsPath, clazz);

            Document doc = JUnitReportAnnotator.newSecureDocumentBuilder().parse(reportPath.toFile());
            List<JUnitReportAnnotator.SkippedTestcase> skipped = annotator.annotateClass(doc, clazz);
            for (JUnitReportAnnotator.SkippedTestcase s : skipped) {
                getLogger().info("testpulse-gradle-plugin: skipping {}#{} ({})", clazz.getName(), s.testcaseName, s.reason);
            }
            writeDocument(doc, reportPath);
        }

        writeAnnotatedCaseKeysFile(annotatedCaseKeys);
    }

    private void writeAnnotatedCaseKeysFile(Set<String> keys) throws Exception {
        File out = annotatedCaseKeysFile.get().getAsFile();
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), String.join("\n", keys).getBytes(StandardCharsets.UTF_8));
    }

    private List<String> collectClassNames() throws Exception {
        List<String> classNames = new ArrayList<>();
        for (File dir : testClassesDirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            Path dirPath = dir.toPath();
            try (Stream<Path> paths = Files.walk(dirPath)) {
                paths.filter(p -> p.toString().endsWith(".class"))
                        .forEach(p -> {
                            String relative = dirPath.relativize(p).toString();
                            String className = relative
                                    .substring(0, relative.length() - ".class".length())
                                    .replace(File.separatorChar, '.');
                            classNames.add(className);
                        });
            }
        }
        return classNames;
    }

    private ClassLoader buildTestClassLoader() throws MalformedURLException {
        List<File> elements = new ArrayList<>(testRuntimeClasspath.getFiles());
        URL[] urls = new URL[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            urls[i] = elements.get(i).toURI().toURL();
        }
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    private void writeDocument(Document doc, Path path) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.transform(new DOMSource(doc), new StreamResult(path.toFile()));
    }
}
