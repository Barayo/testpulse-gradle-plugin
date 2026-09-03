package io.github.barayo.testpulse.gradle;

import io.github.barayo.testpulse.TestPulse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a Gradle-generated {@code TEST-*.xml} document in place, injecting
 * {@code testpulse_*} properties for {@code @TestPulse}-annotated test methods.
 * Ported from testpulse-maven-plugin's SurefireReportAnnotator, which has no
 * Maven-specific dependencies -- the reflection+XML-rewrite mechanism is
 * identical regardless of which build tool ran the tests.
 */
public class JUnitReportAnnotator {

    /** A {@code <testcase>} that could not be matched to exactly one method, for logging. */
    public static final class SkippedTestcase {
        public final String className;
        public final String testcaseName;
        public final String reason;

        SkippedTestcase(String className, String testcaseName, String reason) {
            this.className = className;
            this.testcaseName = testcaseName;
            this.reason = reason;
        }
    }

    /** Returns a hardened parser factory: DOCTYPEs and external entities are rejected. */
    public static DocumentBuilderFactory newSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser does not support the required secure features", e);
        }
        return factory;
    }

    public static DocumentBuilder newSecureDocumentBuilder() {
        try {
            return newSecureDocumentBuilderFactory().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("failed to create a secure XML DocumentBuilder", e);
        }
    }

    /**
     * Injects {@code testpulse_*} properties into every {@code <testcase>} in {@code doc}
     * whose {@code classname} matches {@code testClass} and whose {@code name} resolves to
     * exactly one {@code @TestPulse}-annotated method on that class.
     *
     * @return testcases that were skipped (no exact method match, or no annotation present)
     */
    public List<SkippedTestcase> annotateClass(Document doc, Class<?> testClass) {
        List<SkippedTestcase> skipped = new ArrayList<>();
        NodeList testcases = doc.getElementsByTagName("testcase");

        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcaseEl = (Element) testcases.item(i);
            if (!testClass.getName().equals(testcaseEl.getAttribute("classname"))) {
                continue;
            }

            String name = testcaseEl.getAttribute("name");
            // Gradle's own JUnit Platform (JUnit 5) XML writer appends a bare "()" to a
            // plain no-arg test method's <testcase name> (e.g. "testLoginSucceeds()"),
            // unlike Surefire's bare method name -- strip it before matching, but only
            // when the parens are empty, so a genuinely decorated name (parameters,
            // a parameterized-test invocation) still falls through to "no exact match"
            // rather than being incorrectly normalized.
            String lookupName = name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
            List<Method> matches = findMethodsByName(testClass, lookupName);

            if (matches.size() != 1) {
                skipped.add(new SkippedTestcase(
                        testClass.getName(), name,
                        matches.isEmpty() ? "no exact method match (parameterized/dynamic test?)"
                                : "ambiguous: matches " + matches.size() + " overloaded methods"));
                continue;
            }

            TestPulse annotation = matches.get(0).getAnnotation(TestPulse.class);
            if (annotation == null) {
                continue;
            }

            injectProperties(doc, testcaseEl, annotation);
        }

        return skipped;
    }

    private List<Method> findMethodsByName(Class<?> testClass, String name) {
        List<Method> matches = new ArrayList<>();
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                matches.add(method);
            }
        }
        return matches;
    }

    private void injectProperties(Document doc, Element testcaseEl, TestPulse annotation) {
        Element properties = doc.createElement("properties");

        addProperty(doc, properties, "testpulse_case_key", annotation.caseKey());
        if (!annotation.platform().isEmpty()) {
            addProperty(doc, properties, "testpulse_platform", annotation.platform());
        }
        if (!annotation.version().isEmpty()) {
            addProperty(doc, properties, "testpulse_version", annotation.version());
        }
        if (annotation.tags().length > 0) {
            addProperty(doc, properties, "testpulse_tags", String.join(",", annotation.tags()));
        }

        testcaseEl.appendChild(properties);
    }

    private void addProperty(Document doc, Element properties, String name, String value) {
        Element property = doc.createElement("property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        properties.appendChild(property);
    }
}
