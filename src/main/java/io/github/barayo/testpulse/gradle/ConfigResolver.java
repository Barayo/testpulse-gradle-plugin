package io.github.barayo.testpulse.gradle;

import java.util.function.Function;

/**
 * Resolves each config setting by checking, in order: the corresponding
 * command-line project property ({@code -Ptestpulse.<name>=...}), then
 * (for the token only) the {@code TESTPULSE_TOKEN} environment variable,
 * then the {@code testpulse { }} extension DSL block -- mirroring
 * testpulse-maven-plugin's system-property/env-var/pom-config precedence.
 */
public final class ConfigResolver {
    private final Function<String, String> projectProperty;
    private final Function<String, String> envVar;
    private final TestPulseExtension extension;

    public ConfigResolver(Function<String, String> projectProperty, Function<String, String> envVar, TestPulseExtension extension) {
        this.projectProperty = projectProperty;
        this.envVar = envVar;
        this.extension = extension;
    }

    public String resolveUrl() {
        String fromProp = projectProperty.apply("testpulse.url");
        return fromProp != null ? fromProp : extension.getUrl().getOrNull();
    }

    public String resolveProject() {
        String fromProp = projectProperty.apply("testpulse.project");
        return fromProp != null ? fromProp : extension.getProject().getOrNull();
    }

    public String resolveToken() {
        String fromProp = projectProperty.apply("testpulse.token");
        if (fromProp != null) return fromProp;
        String fromEnv = envVar.apply("TESTPULSE_TOKEN");
        if (fromEnv != null) return fromEnv;
        return extension.getToken().getOrNull();
    }

    public boolean resolveFailOnUnmatched() {
        String fromProp = projectProperty.apply("testpulse.failOnUnmatched");
        if (fromProp != null) return Boolean.parseBoolean(fromProp);
        return extension.getFailOnUnmatched().getOrElse(false);
    }

    public boolean resolveDryRun() {
        String fromProp = projectProperty.apply("testpulse.dryRun");
        if (fromProp != null) return Boolean.parseBoolean(fromProp);
        return extension.getDryRun().getOrElse(false);
    }
}
