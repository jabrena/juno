package io.github.jabrena.juno.conformance;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConformanceCatalogTest {
    private static final Path RESOURCES = Path.of("src/test/resources/conformance");
    private static final Path CATALOG = RESOURCES.resolve("catalog.xml");

    @Test
    void loadsAndValidatesThePrimitiveCatalog() {
        assertThatCode(() -> ConformanceCatalog.load(CATALOG).validate(RESOURCES)).doesNotThrowAnyException();
    }

    @Test
    void catalogContainsEveryPrimitiveFixture() throws Exception {
        ConformanceCatalog catalog = ConformanceCatalog.load(CATALOG);
        try (Stream<Path> files = Files.walk(RESOURCES.resolve("programs"))) {
            long fixtureCount = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java")).count();
            assertThatCode(() -> catalog.validate(RESOURCES)).doesNotThrowAnyException();
            assertThat(catalog.programs()).hasSize((int) fixtureCount);
        }
    }

    @Test
    void rejectsExternalEntities() throws Exception {
        Path directory = Files.createTempDirectory("catalog-");
        Path malicious = directory.resolve("catalog.xml");
        Files.copy(RESOURCES.resolve("catalog.xsd"), directory.resolve("catalog.xsd"));
        try {
            Files.writeString(malicious, """
                    <?xml version="1.0"?>
                    <!DOCTYPE catalog [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                    <catalog javaRelease="27"><programs/><areas>&xxe;</areas></catalog>
                    """);
            assertThatThrownBy(() -> ConformanceCatalog.load(malicious))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot load conformance catalog");
        } finally {
            Files.deleteIfExists(malicious);
            Files.deleteIfExists(directory.resolve("catalog.xsd"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void rejectsCatalogsThatDoNotMatchTheSchema() throws Exception {
        Path directory = Files.createTempDirectory("catalog-");
        Path malformed = directory.resolve("catalog.xml");
        Files.copy(RESOURCES.resolve("catalog.xsd"), directory.resolve("catalog.xsd"));
        try {
            Files.writeString(malformed, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <catalog schemaVersion="1" javaRelease="27"
                             jlsBaseUrl="https://docs.oracle.com/javase/specs/jls/se27/html/">
                        <programs/>
                        <areas/>
                    </catalog>
                    """);
            assertThatThrownBy(() -> ConformanceCatalog.load(malformed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot load conformance catalog");
        } finally {
            Files.deleteIfExists(malformed);
            Files.deleteIfExists(directory.resolve("catalog.xsd"));
            Files.deleteIfExists(directory);
        }
    }
}
