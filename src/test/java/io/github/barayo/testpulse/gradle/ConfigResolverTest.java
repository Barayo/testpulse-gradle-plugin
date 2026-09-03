package io.github.barayo.testpulse.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigResolverTest {

    private TestPulseExtension extension;
    private Map<String, String> projectProps;
    private Map<String, String> env;

    @BeforeEach
    void setUp() {
        Project project = ProjectBuilder.builder().build();
        extension = project.getExtensions().create("testpulse", TestPulseExtension.class);
        projectProps = new HashMap<>();
        env = new HashMap<>();
    }

    private ConfigResolver resolver() {
        return new ConfigResolver(projectProps::get, env::get, extension);
    }

    @Test
    void resolvesUrlFromProjectPropertyAlone() {
        projectProps.put("testpulse.url", "https://from-prop.example");
        assertEquals("https://from-prop.example", resolver().resolveUrl());
    }

    @Test
    void resolvesUrlFromExtensionAlone() {
        extension.getUrl().set("https://from-extension.example");
        assertEquals("https://from-extension.example", resolver().resolveUrl());
    }

    @Test
    void projectPropertyOverridesExtensionForUrl() {
        extension.getUrl().set("https://from-extension.example");
        projectProps.put("testpulse.url", "https://from-prop.example");
        assertEquals("https://from-prop.example", resolver().resolveUrl());
    }

    @Test
    void resolvesTokenFromEnvVarAloneWhenNoProjectProperty() {
        env.put("TESTPULSE_TOKEN", "env-token");
        assertEquals("env-token", resolver().resolveToken());
    }

    @Test
    void projectPropertyOverridesTokenEnvVar() {
        env.put("TESTPULSE_TOKEN", "env-token");
        projectProps.put("testpulse.token", "cli-token");
        assertEquals("cli-token", resolver().resolveToken());
    }

    @Test
    void envVarOverridesExtensionForToken() {
        extension.getToken().set("extension-token");
        env.put("TESTPULSE_TOKEN", "env-token");
        assertEquals("env-token", resolver().resolveToken());
    }

    @Test
    void resolvesFailOnUnmatchedFromProjectPropertyAlone() {
        projectProps.put("testpulse.failOnUnmatched", "true");
        assertTrue(resolver().resolveFailOnUnmatched());
    }

    @Test
    void resolvesFailOnUnmatchedFromExtensionAlone() {
        extension.getFailOnUnmatched().set(true);
        assertTrue(resolver().resolveFailOnUnmatched());
    }

    @Test
    void defaultsFailOnUnmatchedAndDryRunToFalse() {
        assertFalse(resolver().resolveFailOnUnmatched());
        assertFalse(resolver().resolveDryRun());
    }

    @Test
    void resolvesDryRunFromProjectPropertyAlone() {
        projectProps.put("testpulse.dryRun", "true");
        assertTrue(resolver().resolveDryRun());
    }

    @Test
    void unresolvedProjectKeyIsNull() {
        assertNull(resolver().resolveProject());
    }
}
