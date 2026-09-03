package io.github.barayo.testpulse.gradle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestClassLoaderTest {

    static class Healthy {
    }

    static class BrokenStaticInit {
        static {
            if (true) {
                throw new RuntimeException("boom");
            }
        }
    }

    @Test
    void skipsClassThatFailsToLoadAndContinuesWithTheRest() {
        List<String> classNames = Arrays.asList(
                Healthy.class.getName(),
                BrokenStaticInit.class.getName());

        TestClassLoader.LoadResult result =
                new TestClassLoader().loadAll(classNames, getClass().getClassLoader());

        assertEquals(1, result.loaded.size());
        assertEquals(Healthy.class, result.loaded.get(0));
        assertEquals(1, result.failed.size());
        assertTrue(result.failed.get(0).className.equals(BrokenStaticInit.class.getName()));
    }
}
