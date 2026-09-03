package io.github.barayo.testpulse.gradle;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads a project's compiled test classes by name, isolating a single class's load
 * failure (missing dependency, static-initializer error) from the rest of the scan.
 */
public class TestClassLoader {

    /** A class that failed to load, for logging. */
    public static final class FailedClass {
        public final String className;
        public final Throwable cause;

        FailedClass(String className, Throwable cause) {
            this.className = className;
            this.cause = cause;
        }
    }

    public static final class LoadResult {
        public final List<Class<?>> loaded;
        public final List<FailedClass> failed;

        LoadResult(List<Class<?>> loaded, List<FailedClass> failed) {
            this.loaded = loaded;
            this.failed = failed;
        }
    }

    public LoadResult loadAll(List<String> classNames, ClassLoader classLoader) {
        List<Class<?>> loaded = new ArrayList<>();
        List<FailedClass> failed = new ArrayList<>();

        for (String className : classNames) {
            try {
                loaded.add(Class.forName(className, true, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                failed.add(new FailedClass(className, e));
            }
        }

        return new LoadResult(loaded, failed);
    }
}
