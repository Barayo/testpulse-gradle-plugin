package io.github.barayo.testpulse.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPulsePluginTest {

    @Test
    void registersTheExtensionAndBothTasks() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(TestPulsePlugin.class);

        assertNotNull(project.getExtensions().findByName("testpulse"));
        assertNotNull(project.getTasks().findByName("testpulseAnnotate"));
        assertNotNull(project.getTasks().findByName("testpulseSubmit"));
    }

    @Test
    void addsTestpulseAnnotationsAsATestImplementationDependency() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(TestPulsePlugin.class);

        boolean hasAnnotationsDep = project.getConfigurations().getByName("testImplementation").getDependencies()
                .stream().anyMatch(d -> "testpulse-annotations".equals(d.getName()));
        assertTrue(hasAnnotationsDep, "expected testpulse-annotations on testImplementation");
    }

    @Test
    void addsItsOwnClassesAsATestImplementationDependencySoTestPulseAttachmentsIsVisible() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(TestPulsePlugin.class);

        long fileDependencyCount = project.getConfigurations().getByName("testImplementation").getDependencies()
                .stream().filter(d -> d instanceof org.gradle.api.artifacts.SelfResolvingDependency).count();
        assertTrue(fileDependencyCount >= 1, "expected the plugin's own classes added as a file dependency");
    }
}
