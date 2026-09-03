package io.github.barayo.testpulse.gradle;

import io.github.barayo.testpulse.TestPulse;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/** Collects the {@code @TestPulse} case keys declared on a class's own methods. */
public final class AnnotatedCaseKeys {
    private AnnotatedCaseKeys() {
    }

    public static Set<String> forClass(Class<?> testClass) {
        Set<String> keys = new LinkedHashSet<>();
        for (Method method : testClass.getDeclaredMethods()) {
            TestPulse annotation = method.getAnnotation(TestPulse.class);
            if (annotation != null) {
                keys.add(annotation.caseKey());
            }
        }
        return keys;
    }
}
