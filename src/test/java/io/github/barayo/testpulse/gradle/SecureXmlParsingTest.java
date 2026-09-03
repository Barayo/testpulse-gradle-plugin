package io.github.barayo.testpulse.gradle;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureXmlParsingTest {

    @Test
    void rejectsDoctypeWithExternalEntity(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path secretFile = tmp.resolve("secret.txt");
        Files.writeString(secretFile, "super-secret-value");

        String maliciousXml =
                "<?xml version=\"1.0\"?>"
                        + "<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM \"file://" + secretFile + "\">]>"
                        + "<testsuite name=\"&xxe;\"><testcase name=\"t\" classname=\"c\"/></testsuite>";

        DocumentBuilder builder = JUnitReportAnnotator.newSecureDocumentBuilder();

        // A hardened parser configuration rejects the DOCTYPE outright (SAXParseException),
        // rather than silently resolving the external entity into "super-secret-value".
        assertThrows(SAXException.class, () ->
                builder.parse(new ByteArrayInputStream(maliciousXml.getBytes(StandardCharsets.UTF_8))));
    }
}
