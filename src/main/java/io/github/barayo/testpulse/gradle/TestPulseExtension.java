package io.github.barayo.testpulse.gradle;

import org.gradle.api.provider.Property;

/** The {@code testpulse { }} extension DSL block. */
public interface TestPulseExtension {
    Property<String> getUrl();

    Property<String> getToken();

    Property<String> getProject();

    Property<Boolean> getFailOnUnmatched();

    Property<Boolean> getDryRun();
}
