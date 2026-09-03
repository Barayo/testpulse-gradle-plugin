package io.github.barayo.testpulse.gradle;

import io.github.barayo.testpulse.TestPulse;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JUnitReportAnnotatorTest {

    static class SampleTest {
        @TestPulse(caseKey = "LOGIN-42")
        void testLoginSucceeds() {
        }

        @TestPulse(caseKey = "LOGIN-42", platform = "linux", tags = {"smoke"})
        void testLoginWithMetadata() {
        }

        void testPlain() {
        }
    }

    static class OverloadedTest {
        @TestPulse(caseKey = "LOGIN-42")
        void testLogin(String user) {
        }

        void testLogin() {
        }
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private Element testcase(Document doc, String name) {
        NodeList nodes = doc.getElementsByTagName("testcase");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            if (el.getAttribute("name").equals(name)) {
                return el;
            }
        }
        throw new AssertionError("no <testcase name=\"" + name + "\"> found");
    }

    private String propertyValue(Element testcaseEl, String propName) {
        NodeList props = testcaseEl.getElementsByTagName("property");
        for (int i = 0; i < props.getLength(); i++) {
            Element prop = (Element) props.item(i);
            if (prop.getAttribute("name").equals(propName)) {
                return prop.getAttribute("value");
            }
        }
        return null;
    }

    @Test
    void injectsCaseKeyForAnnotatedMethod() throws Exception {
        Document doc = parse(
                "<testsuite name=\"" + SampleTest.class.getName() + "\">"
                        + "<testcase classname=\"" + SampleTest.class.getName() + "\" name=\"testLoginSucceeds\"/>"
                        + "</testsuite>");

        JUnitReportAnnotator annotator = new JUnitReportAnnotator();
        annotator.annotateClass(doc, SampleTest.class);

        Element el = testcase(doc, "testLoginSucceeds");
        assertEquals("LOGIN-42", propertyValue(el, "testpulse_case_key"));
    }

    @Test
    void injectsOptionalMetadataOnlyWhenSupplied() throws Exception {
        Document doc = parse(
                "<testsuite name=\"" + SampleTest.class.getName() + "\">"
                        + "<testcase classname=\"" + SampleTest.class.getName() + "\" name=\"testLoginWithMetadata\"/>"
                        + "</testsuite>");

        new JUnitReportAnnotator().annotateClass(doc, SampleTest.class);

        Element el = testcase(doc, "testLoginWithMetadata");
        assertEquals("linux", propertyValue(el, "testpulse_platform"));
        assertEquals("smoke", propertyValue(el, "testpulse_tags"));
        assertNull(propertyValue(el, "testpulse_version"));
    }

    @Test
    void leavesUnannotatedTestcaseUntouched() throws Exception {
        Document doc = parse(
                "<testsuite name=\"" + SampleTest.class.getName() + "\">"
                        + "<testcase classname=\"" + SampleTest.class.getName() + "\" name=\"testPlain\"/>"
                        + "</testsuite>");

        new JUnitReportAnnotator().annotateClass(doc, SampleTest.class);

        Element el = testcase(doc, "testPlain");
        assertNull(propertyValue(el, "testpulse_case_key"));
    }

    @Test
    void matchesAGradleJUnit5StyleNameWithTrailingEmptyParens() throws Exception {
        // Gradle's own JUnit Platform XML writer appends a bare "()" to a plain no-arg
        // test method's <testcase name> (e.g. "testLoginSucceeds()"), unlike Surefire's
        // bare method name -- confirmed via a real Gradle TestKit functional test run.
        Document doc = parse(
                "<testsuite name=\"" + SampleTest.class.getName() + "\">"
                        + "<testcase classname=\"" + SampleTest.class.getName() + "\" name=\"testLoginSucceeds()\"/>"
                        + "</testsuite>");

        new JUnitReportAnnotator().annotateClass(doc, SampleTest.class);

        Element el = testcase(doc, "testLoginSucceeds()");
        assertEquals("LOGIN-42", propertyValue(el, "testpulse_case_key"));
    }

    @Test
    void leavesDecoratedParameterizedNameUnmodifiedAndReportsSkip() throws Exception {
        // Simulates a Gradle-decorated parameterized-test invocation name -- doesn't map
        // to any exact declared method name on the class.
        Document doc = parse(
                "<testsuite name=\"" + SampleTest.class.getName() + "\">"
                        + "<testcase classname=\"" + SampleTest.class.getName() + "\" name=\"testLoginSucceeds(String)[1]\"/>"
                        + "</testsuite>");

        List<JUnitReportAnnotator.SkippedTestcase> skipped =
                new JUnitReportAnnotator().annotateClass(doc, SampleTest.class);

        Element el = testcase(doc, "testLoginSucceeds(String)[1]");
        assertNull(propertyValue(el, "testpulse_case_key"));
        assertEquals(1, skipped.size());
        assertEquals("testLoginSucceeds(String)[1]", skipped.get(0).testcaseName);
    }

    @Test
    void leavesOverloadedMethodNameUnmodifiedAsAmbiguous() throws Exception {
        Document doc = parse(
                "<testsuite name=\"" + OverloadedTest.class.getName() + "\">"
                        + "<testcase classname=\"" + OverloadedTest.class.getName() + "\" name=\"testLogin\"/>"
                        + "</testsuite>");

        List<JUnitReportAnnotator.SkippedTestcase> skipped =
                new JUnitReportAnnotator().annotateClass(doc, OverloadedTest.class);

        Element el = testcase(doc, "testLogin");
        assertNull(propertyValue(el, "testpulse_case_key"));
        assertEquals(1, skipped.size());
        assertEquals("ambiguous: matches 2 overloaded methods", skipped.get(0).reason);
    }
}
