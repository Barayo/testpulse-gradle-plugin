package io.github.barayo.testpulse.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportCombinerTest {

    @TempDir
    Path reportsDir;

    @Test
    void combinesMultipleTestsuiteFilesIntoOneValidTestsuitesDocument() throws Exception {
        Files.writeString(reportsDir.resolve("TEST-A.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"A\"><testcase name=\"t1\" classname=\"A\"/></testsuite>",
                StandardCharsets.UTF_8);
        Files.writeString(reportsDir.resolve("TEST-B.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"B\"><testcase name=\"t2\" classname=\"B\"/></testsuite>",
                StandardCharsets.UTF_8);

        String combined = ReportCombiner.combine(reportsDir);

        // The combined string must itself be a single, well-formed XML document
        // (one root, one prolog) -- unlike naive string concatenation, which
        // produces multiple top-level documents that Go's encoding/xml silently
        // truncates after the first one (confirmed against the real backend parser).
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));

        assertEquals("testsuites", doc.getDocumentElement().getTagName());
        org.w3c.dom.NodeList testsuites = doc.getElementsByTagName("testsuite");
        assertEquals(2, testsuites.getLength());
        org.w3c.dom.NodeList testcases = doc.getElementsByTagName("testcase");
        assertEquals(2, testcases.getLength());
    }

    @Test
    void returnsEmptyTestsuitesDocumentWhenNoReportsExist() throws Exception {
        String combined = ReportCombiner.combine(reportsDir);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        org.w3c.dom.Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("testsuites", doc.getDocumentElement().getTagName());
        assertEquals(0, doc.getElementsByTagName("testsuite").getLength());
    }
}
