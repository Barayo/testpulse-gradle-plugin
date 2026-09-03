package io.github.barayo.testpulse.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;

import java.io.File;

/**
 * Applies the TestPulse import mechanism to a Gradle project: registers
 * the {@code testpulse { }} extension, the {@code testpulseAnnotate}/
 * {@code testpulseSubmit} tasks (auto-wired via {@code finalizedBy} so a
 * plain {@code ./gradlew test} runs the whole pipeline), and adds
 * {@code testpulse-annotations} as a {@code testImplementation}
 * dependency automatically so consumers don't need to add it themselves.
 */
public class TestPulsePlugin implements Plugin<Project> {
    private static final String ANNOTATIONS_DEPENDENCY = "io.github.barayo:testpulse-annotations:1.0.0";

    @Override
    public void apply(Project project) {
        TestPulseExtension extension = project.getExtensions().create("testpulse", TestPulseExtension.class);
        extension.getFailOnUnmatched().convention(false);
        extension.getDryRun().convention(false);

        project.getPluginManager().withPlugin("java", appliedPlugin -> {
            project.getDependencies().add("testImplementation", ANNOTATIONS_DEPENDENCY);
            // Applying the plugin (plugins { id(...) }) only puts this plugin's own jar
            // on the buildscript classpath, not on the consuming project's own
            // src/test/java compile classpath -- so TestPulseAttachments wouldn't be
            // visible to test code without this. Adding the jar/classes currently
            // backing this exact plugin class (works whether that's a real published
            // jar or TestKit's withPluginClasspath()-injected classpath) as a file
            // dependency avoids needing to know/hardcode this plugin's own version.
            project.getDependencies().add("testImplementation",
                    project.files(TestPulsePlugin.class.getProtectionDomain().getCodeSource().getLocation()));

            SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
            SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);

            File scratchDir = project.getLayout().getBuildDirectory().dir("testpulse").get().getAsFile();

            Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
            testTask.getSystemProperties().put("testpulse.scratchDir", scratchDir.getAbsolutePath());
            // Cleared exactly once, before the Test task's own action runs (i.e. before
            // any forked test JVM could call attach()) -- otherwise attachments from a
            // stale prior run (a workspace without an intervening `clean`) would
            // accumulate indefinitely and leak into a later, unrelated submission. This
            // is the Gradle-task equivalent of testpulse-jest's onRunStart cleanup.
            testTask.doFirst(task -> project.delete(scratchDir));

            // Report.outputLocation is a Provider owned by the Test task's own output
            // tracking; chaining it directly as another task's declared input confuses
            // Gradle's task-graph analysis ("does not have a task associated with it").
            // Resolved eagerly here instead -- correct for the default convention and
            // for reconfiguration that happens before this plugin's apply() block runs,
            // which covers the overwhelming majority of real build scripts.
            File testResultsDir = testTask.getReports().getJunitXml().getOutputLocation().get().getAsFile();

            TestpulseAnnotateTask annotateTask = project.getTasks().create(
                    "testpulseAnnotate", TestpulseAnnotateTask.class, task -> {
                        task.getTestClassesDirs().setFrom(testSourceSet.getOutput().getClassesDirs());
                        task.getTestRuntimeClasspath().setFrom(testSourceSet.getRuntimeClasspath());
                        task.getTestResultsDir().set(testResultsDir);
                        task.getAnnotatedCaseKeysFile().set(project.getLayout().getBuildDirectory().file("testpulse/annotated-case-keys.txt"));
                    });
            annotateTask.dependsOn(testTask);

            TestpulseSubmitTask submitTask = project.getTasks().create(
                    "testpulseSubmit", TestpulseSubmitTask.class, task -> {
                        task.getTestResultsDir().set(testResultsDir);
                        task.getAnnotatedCaseKeysFile().set(annotateTask.getAnnotatedCaseKeysFile());
                        task.getScratchDir().set(project.getLayout().getBuildDirectory().dir("testpulse"));
                        task.getExtension().set(extension);
                    });
            submitTask.dependsOn(annotateTask);

            testTask.finalizedBy(annotateTask);
            annotateTask.finalizedBy(submitTask);
        });
    }
}
