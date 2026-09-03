package io.github.barayo.testpulse.gradle;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Combines multiple {@code TEST-*.xml} reports into a single, well-formed
 * document -- required because the import API's parser (Go's
 * {@code encoding/xml}) decodes only the first top-level XML element in a
 * string and silently stops there, with no error, if more follow. Naive
 * string concatenation of complete documents (each with its own prolog and
 * root element) produced multiple top-level documents, so every class but
 * the first was silently dropped -- confirmed against the real backend
 * parser (apps/api/internal/junitxml/parser.go), which does support a
 * single {@code <testsuites>} root wrapping repeated {@code <testsuite>}
 * children.
 */
public final class ReportCombiner {
    private ReportCombiner() {
    }

    public static String combine(Path testResultsDir) throws Exception {
        Document combined = JUnitReportAnnotator.newSecureDocumentBuilder().newDocument();
        Element root = combined.createElement("testsuites");
        combined.appendChild(root);

        if (Files.isDirectory(testResultsDir)) {
            try (Stream<Path> reports = Files.list(testResultsDir)) {
                for (Path report : (Iterable<Path>) reports.filter(p -> p.toString().endsWith(".xml"))::iterator) {
                    Document doc = JUnitReportAnnotator.newSecureDocumentBuilder().parse(report.toFile());
                    Element imported = (Element) combined.importNode(doc.getDocumentElement(), true);
                    root.appendChild(imported);
                }
            }
        }

        return serialize(combined);
    }

    private static String serialize(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
