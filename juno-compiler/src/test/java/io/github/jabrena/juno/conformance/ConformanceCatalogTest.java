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
    void loadsAndValidatesTheConformanceCatalog() {
        assertThatCode(() -> ConformanceCatalog.load(CATALOG).validate(RESOURCES)).doesNotThrowAnyException();
    }

    @Test
    void reportsCoverageAgainstEveryJlsSectionInScope() {
        ConformanceCatalog catalog = ConformanceCatalog.load(CATALOG);
        assertThat(catalog.coverage()).extracting(ConformanceCatalog.Coverage::scopeId)
                .containsExactly("arrays", "controlflow", "generics", "inheritance", "primitives", "records", "sealed",
                        "strings");
        for (ConformanceCatalog.Coverage coverage : catalog.coverage()) {
            System.out.println(coverage.summary());
            assertThat(coverage.covered() + coverage.gaps() + coverage.otherCategory() + coverage.outOfScope())
                    .isEqualTo(coverage.total());
        }
    }

    @Test
    void rejectsJlsSectionThatIsNeitherCoveredNorSkipped() throws Exception {
        assertRejected("<jls section=\"3.10.8\" skipped=\"true\" reason=\"Out of scope: the null literal is a "
                + "reference-type value\"/>", "", "neither covered nor skipped");
    }

    @Test
    void rejectsJlsSectionClaimedAsCoveredAndSkipped() throws Exception {
        assertRejected("<program ref=\"primitive-literals\"/>",
                "<jls section=\"3.10.8\"/>\n            <program ref=\"primitive-literals\"/>",
                "classified both as");
    }

    @Test
    void rejectsSkippedJlsSectionWithoutReason() throws Exception {
        assertRejected(" reason=\"Out of scope: the null literal is a reference-type value\"", "",
                "Missing reason");
    }

    @Test
    void rejectsCoveredAreaWithoutPrograms() throws Exception {
        assertRejected("<program ref=\"primitive-literals\"/>", "", "primitives.literals");
    }

    @Test
    void rejectsJlsSectionOutsideItsScope() throws Exception {
        assertRejected("<program ref=\"primitive-literals\"/>",
                "<jls section=\"9.9\" skipped=\"true\" reason=\"Gap: not a primitive section\"/>\n"
                        + "            <program ref=\"primitive-literals\"/>",
                "outside scope primitives");
    }

    @Test
    void rejectsCatalogDefiningABackendOutsideTheRequiredSet() throws Exception {
        String catalog = Files.readString(CATALOG);
        String mutated = catalog.replace("<backends>",
                "<backends>\n        <backend id=\"wasm\" title=\"Not a required backend\"/>");
        assertRejectedCatalog(mutated, "must define backends");
    }

    @Test
    void rejectsControlflowAreaWithAnUnknownBackend() throws Exception {
        String catalog = Files.readString(CATALOG);
        int start = catalog.indexOf("<area id=\"controlflow.");
        String mutated = catalog.substring(0, start)
                + catalog.substring(start).replaceFirst("ref=\"avr\"", "ref=\"avx\"");
        assertRejectedCatalog(mutated, "Cannot load conformance catalog");
    }

    @Test
    void rejectsControlflowAreaWithFewerThanThreeBackends() throws Exception {
        String catalog = Files.readString(CATALOG);
        int start = catalog.indexOf("<area id=\"controlflow.");
        String mutated = catalog.substring(0, start)
                + catalog.substring(start).replaceFirst("            <backend ref=\"avr\" status=\"REQUIRED\"/>\n", "");
        assertRejectedCatalog(mutated, "Cannot load conformance catalog");
    }

    /** Applies one textual mutation to the real catalog and asserts that validation rejects it. */
    private static void assertRejected(String original, String replacement, String message) throws Exception {
        String catalog = Files.readString(CATALOG);
        assertThat(catalog).contains(original);
        assertRejectedCatalog(catalog.replace(original, replacement), message);
    }

    private static void assertRejectedCatalog(String catalog, String message) throws Exception {
        Path directory = Files.createTempDirectory("catalog-");
        Path mutated = directory.resolve("catalog.xml");
        try {
            Files.copy(RESOURCES.resolve("catalog.xsd"), directory.resolve("catalog.xsd"));
            Files.writeString(mutated, catalog);
            assertThatThrownBy(() -> ConformanceCatalog.load(mutated).validate(RESOURCES))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(message);
        } finally {
            Files.deleteIfExists(mutated);
            Files.deleteIfExists(directory.resolve("catalog.xsd"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void catalogContainsEveryConformanceFixture() throws Exception {
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
