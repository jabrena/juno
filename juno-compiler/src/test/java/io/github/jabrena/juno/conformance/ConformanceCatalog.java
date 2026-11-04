package io.github.jabrena.juno.conformance;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/** Parsed and validated semantic-conformance catalog used by the emulator integration tests. */
public final class ConformanceCatalog {
    private static final Set<String> REQUIRED_BACKENDS = Set.of("thumb2", "avr", "xtensa");
    // Map.of accepts at most 10 pairs; this catalog has 13 areas, so entries are built with Map.ofEntries.
    private static final Map<String, Set<String>> REQUIRED_AREAS = Map.ofEntries(
            Map.entry("primitives.literals", Set.of("3.10.1", "3.10.2", "3.10.3", "3.10.4")),
            Map.entry("primitives.integral-types", Set.of("4.2.1", "4.2.2")),
            Map.entry("primitives.floating-point-types", Set.of("4.2.3", "4.2.4")),
            Map.entry("primitives.boolean", Set.of("4.2.5")),
            Map.entry("primitives.conversions", Set.of("5.1.2", "5.1.3", "5.1.4", "5.2", "5.5", "5.6.1", "5.6.2")),
            Map.entry("primitives.integral-expressions", Set.of("15.15.1", "15.15.2", "15.15.3", "15.16", "15.17.1",
                    "15.17.2", "15.17.3", "15.18.2", "15.19", "15.20", "15.21", "15.22.1")),
            Map.entry("primitives.floating-point-expressions", Set.of("15.15.1", "15.15.2", "15.16", "15.17.1",
                    "15.17.2", "15.17.3", "15.18.2", "15.20", "15.21")),
            Map.entry("primitives.boolean-and-conditional", Set.of("15.7", "15.21", "15.22.2", "15.23", "15.24",
                    "15.25")),
            Map.entry("records.components-members-and-constructors", Set.of("8.10", "8.10.1", "8.10.2", "8.10.3",
                    "8.10.4", "8.10.4.1", "8.10.4.2")),
            Map.entry("sealed.classes-and-interfaces", Set.of("8.1.1.2", "8.1.6", "9.1.1.4", "9.1.4")),
            Map.entry("inheritance.class-and-interface-dispatch", Set.of("8.1.4", "8.1.5", "8.4.8.1", "15.12.4.4")),
            Map.entry("generics.classes-methods-and-erasure", Set.of("4.4", "4.5", "4.6", "8.1.2", "8.4.4")),
            Map.entry("arrays.creation-access-and-length", Set.of("10.1", "10.2", "10.3", "10.4", "10.5", "10.6",
                    "10.7", "15.10.1", "15.10.2", "15.10.3")));

    /** Classification value of a JLS section that some area covers with a program. */
    private static final String COVERED = "covered";

    private final int javaRelease;
    private final Map<String, Program> programs;
    private final Set<String> backends;
    private final Map<String, Scope> scopes;
    private final Map<String, Area> areas;

    private ConformanceCatalog(int javaRelease, Map<String, Program> programs, Set<String> backends,
            Map<String, Scope> scopes, Map<String, Area> areas) {
        this.javaRelease = javaRelease;
        this.programs = Map.copyOf(programs);
        this.backends = Set.copyOf(backends);
        this.scopes = Map.copyOf(scopes);
        this.areas = Map.copyOf(areas);
    }

    /** Loads the XML catalog using a parser that cannot resolve external entities or resources. */
    public static ConformanceCatalog load(Path catalogPath) {
        try {
            var builder = secureFactory(schemaPath(catalogPath)).newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(org.xml.sax.SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            Document document = builder.parse(catalogPath.toFile());
            Element root = document.getDocumentElement();
            requireTag(root, "catalog");
            int javaRelease = parseInt(root, "javaRelease");
            Map<String, Program> programs = parsePrograms(root);
            Set<String> backends = parseBackends(root);
            Map<String, Scope> scopes = parseScopes(root);
            Map<String, Area> areas = parseAreas(root);
            return new ConformanceCatalog(javaRelease, programs, backends, scopes, areas);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalArgumentException("Cannot load conformance catalog " + catalogPath, e);
        }
    }

    /** Validates catalog relationships and its fixture inventory against the test-resource tree. */
    public void validate(Path conformanceRoot) {
        if (javaRelease != 27) {
            throw new IllegalArgumentException("Conformance catalog must target Java SE 27, not " + javaRelease);
        }
        if (programs.isEmpty()) {
            throw new IllegalArgumentException("Conformance catalog has no program inventory");
        }
        if (areas.isEmpty()) {
            throw new IllegalArgumentException("Conformance catalog has no semantic areas");
        }
        validateRequiredAreas();
        validateProgramInventory(conformanceRoot.resolve("programs"));
        validateAreaReferences();
        validateBackendDefinitions();
        validateClassification();
    }

    public List<Program> programs() {
        return programs.values().stream().sorted().toList();
    }

    public List<Area> areas() {
        return areas.values().stream().sorted().toList();
    }

    public List<Scope> scopes() {
        return scopes.values().stream().sorted().toList();
    }

    /** Covered and skipped JLS sections per scope, so the share still missing is visible, not implied. */
    public List<Coverage> coverage() {
        return scopes.values().stream().sorted().map(scope -> coverageOf(scope, classify(scope))).toList();
    }

    private void validateRequiredAreas() {
        for (Map.Entry<String, Set<String>> required : REQUIRED_AREAS.entrySet()) {
            Area area = areas.get(required.getKey());
            if (area == null) {
                throw new IllegalArgumentException("Missing required semantic area " + required.getKey());
            }
            if (!area.jlsSections().containsAll(required.getValue())) {
                Set<String> missing = new HashSet<>(required.getValue());
                missing.removeAll(area.jlsSections());
                throw new IllegalArgumentException("Area " + area.id() + " is missing JLS sections " + missing);
            }
            if (area.programIds().isEmpty()) {
                throw new IllegalArgumentException("Semantic area has no programs: " + area.id());
            }
            if (!area.backendStatuses().keySet().containsAll(REQUIRED_BACKENDS)) {
                Set<String> missing = new HashSet<>(REQUIRED_BACKENDS);
                missing.removeAll(area.backendStatuses().keySet());
                throw new IllegalArgumentException("Area " + area.id() + " is missing backends " + missing);
            }
        }
    }

    private void validateProgramInventory(Path programRoot) {
        Set<String> files;
        try (Stream<Path> paths = Files.walk(programRoot)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(programRoot::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace(File.separatorChar, '/'))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot scan conformance program resources " + programRoot, e);
        }
        Set<String> inventory = programs.values().stream()
                .map(program -> program.source().substring("programs/".length()))
                .collect(Collectors.toSet());
        Set<String> unlisted = new HashSet<>(files);
        unlisted.removeAll(inventory);
        if (!unlisted.isEmpty()) {
            throw new IllegalArgumentException("Conformance fixtures missing from catalog inventory: " + unlisted);
        }
        Set<String> missing = new HashSet<>(inventory);
        missing.removeAll(files);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Catalog inventory fixtures do not exist: " + missing);
        }
        for (Program program : programs.values()) {
            Path source = programRoot.getParent().resolve(program.source()).normalize();
            if (!source.startsWith(programRoot)) {
                throw new IllegalArgumentException("Catalog source escapes program resources: " + program.source());
            }
        }
    }

    private void validateAreaReferences() {
        Set<String> referenced = new HashSet<>();
        for (Area area : areas.values()) {
            for (String programId : area.programIds()) {
                if (!programs.containsKey(programId)) {
                    throw new IllegalArgumentException("Area " + area.id() + " references unknown program " + programId);
                }
                referenced.add(programId);
            }
        }
        Set<String> unclassified = new HashSet<>(programs.keySet());
        unclassified.removeAll(referenced);
        if (!unclassified.isEmpty()) {
            throw new IllegalArgumentException("Catalog programs are not assigned to an area: " + unclassified);
        }
    }

    private static Map<String, Program> parsePrograms(Element root) {
        Element programsElement = child(root, "programs");
        Map<String, Program> programs = new LinkedHashMap<>();
        for (Element element : children(programsElement, "program")) {
            String id = requiredAttribute(element, "id");
            String source = requiredAttribute(element, "source");
            if (!source.startsWith("programs/") || !source.endsWith(".java")) {
                throw new IllegalArgumentException("Program source must be a Java fixture below programs/: " + source);
            }
            putUnique(programs, id, new Program(id, source));
        }
        return programs;
    }

    private static Set<String> parseBackends(Element root) {
        Element backendsElement = child(root, "backends");
        Map<String, String> definitions = new LinkedHashMap<>();
        for (Element element : children(backendsElement, "backend")) {
            putUnique(definitions, requiredAttribute(element, "id"), requiredAttribute(element, "title"));
        }
        return definitions.keySet();
    }

    private static Map<String, Scope> parseScopes(Element root) {
        Element scopesElement = child(root, "scopes");
        Map<String, Scope> scopes = new LinkedHashMap<>();
        for (Element element : children(scopesElement, "scope")) {
            String id = requiredAttribute(element, "id");
            List<String> sections = children(element, "jls").stream()
                    .map(section -> requiredAttribute(section, "section"))
                    .toList();
            if (new HashSet<>(sections).size() != sections.size()) {
                throw new IllegalArgumentException("Scope " + id + " lists a JLS section twice");
            }
            putUnique(scopes, id, new Scope(id, requiredAttribute(element, "title"), sections));
        }
        return scopes;
    }

    private static Map<String, Area> parseAreas(Element root) {
        Element areasElement = child(root, "areas");
        Map<String, Area> areas = new LinkedHashMap<>();
        for (Element element : children(areasElement, "area")) {
            String id = requiredAttribute(element, "id");
            String title = requiredAttribute(element, "title");
            List<String> covered = new ArrayList<>();
            Map<String, String> skipped = new LinkedHashMap<>();
            for (Element jls : children(element, "jls")) {
                String section = requiredAttribute(jls, "section");
                if (isSkipped(jls)) {
                    putUnique(skipped, section, requiredAttribute(jls, "reason"));
                } else {
                    if (!jls.getAttribute("reason").isEmpty()) {
                        throw new IllegalArgumentException(
                                "JLS section " + section + " has a reason but is not skipped");
                    }
                    covered.add(section);
                }
            }
            List<String> programIds = children(element, "program").stream()
                    .map(program -> requiredAttribute(program, "ref"))
                    .distinct().toList();
            Map<String, String> backendStatuses = new LinkedHashMap<>();
            for (Element backend : children(element, "backend")) {
                putUnique(backendStatuses, requiredAttribute(backend, "ref"), requiredAttribute(backend, "status"));
            }
            putUnique(areas, id, new Area(id, title, covered.stream().distinct().toList(), skipped, programIds,
                    backendStatuses));
        }
        return areas;
    }

    private static boolean isSkipped(Element jls) {
        String skipped = jls.getAttribute("skipped");
        return skipped.equals("true") || skipped.equals("1");
    }

    /** The category an area belongs to is the prefix of its id, such as {@code primitives} in {@code primitives.literals}. */
    private static String categoryOf(Area area) {
        int dot = area.id().indexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("Area id must be <category>.<name>: " + area.id());
        }
        return area.id().substring(0, dot);
    }

    /** The catalog defines exactly the required backends, and every area refers only to defined ones. */
    private void validateBackendDefinitions() {
        if (!backends.equals(REQUIRED_BACKENDS)) {
            throw new IllegalArgumentException(
                    "Catalog must define backends " + REQUIRED_BACKENDS + " but defines " + backends);
        }
        for (Area area : areas.values()) {
            for (String backend : area.backendStatuses().keySet()) {
                if (!backends.contains(backend)) {
                    throw new IllegalArgumentException(
                            "Area " + area.id() + " refers to backend " + backend + " which is not defined in <backends>");
                }
            }
        }
    }

    /** Every area section must belong to its category's scope, and every scope section must be classified once. */
    private void validateClassification() {
        for (Area area : areas.values()) {
            Scope scope = scopes.get(categoryOf(area));
            if (scope == null) {
                throw new IllegalArgumentException("Area " + area.id() + " belongs to no scope");
            }
            Set<String> cited = new HashSet<>(area.jlsSections());
            cited.addAll(area.skippedSections().keySet());
            for (String section : cited) {
                if (!scope.jlsSections().contains(section)) {
                    throw new IllegalArgumentException(
                            "Area " + area.id() + " cites JLS section " + section + " outside scope " + scope.id());
                }
            }
            if (!area.backendStatuses().keySet().equals(REQUIRED_BACKENDS)) {
                throw new IllegalArgumentException("Area " + area.id() + " must declare backends " + REQUIRED_BACKENDS
                        + " but declares " + area.backendStatuses().keySet());
            }
            if (!area.jlsSections().isEmpty() && area.programIds().isEmpty()) {
                throw new IllegalArgumentException("Area " + area.id() + " covers JLS sections without programs");
            }
        }
        for (Scope scope : scopes.values()) {
            Set<String> unclassified = new LinkedHashSet<>(scope.jlsSections());
            unclassified.removeAll(classify(scope).keySet());
            if (!unclassified.isEmpty()) {
                throw new IllegalArgumentException(
                        "Scope " + scope.id() + " has JLS sections neither covered nor skipped: " + unclassified);
            }
        }
    }

    /** Maps each section of a scope to {@link #COVERED} or to its skip reason. Conflicting claims are rejected. */
    private Map<String, String> classify(Scope scope) {
        Map<String, String> status = new LinkedHashMap<>();
        for (Area area : areas.values()) {
            if (!categoryOf(area).equals(scope.id())) {
                continue;
            }
            for (String section : area.jlsSections()) {
                recordClaim(status, section, COVERED);
            }
            for (Map.Entry<String, String> skipped : area.skippedSections().entrySet()) {
                recordClaim(status, skipped.getKey(), skipped.getValue());
            }
        }
        return status;
    }

    private static void recordClaim(Map<String, String> status, String section, String value) {
        String previous = status.putIfAbsent(section, value);
        if (previous != null && !previous.equals(value)) {
            throw new IllegalArgumentException(
                    "JLS section " + section + " is classified both as '" + previous + "' and '" + value + "'");
        }
    }

    private static Coverage coverageOf(Scope scope, Map<String, String> status) {
        int covered = 0;
        int gaps = 0;
        int otherCategory = 0;
        int outOfScope = 0;
        for (String value : status.values()) {
            if (value.equals(COVERED)) {
                covered++;
            } else if (value.startsWith("Gap:")) {
                gaps++;
            } else if (value.startsWith("Other category:")) {
                otherCategory++;
            } else {
                outOfScope++;
            }
        }
        return new Coverage(scope.id(), scope.jlsSections().size(), covered, gaps, otherCategory, outOfScope);
    }

    private static DocumentBuilderFactory secureFactory(Path schemaPath)
            throws ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setSchema(loadSchema(schemaPath));
        return factory;
    }

    private static Path schemaPath(Path catalogPath) {
        Path absoluteCatalog = catalogPath.toAbsolutePath().normalize();
        return absoluteCatalog.resolveSibling("catalog.xsd");
    }

    private static Schema loadSchema(Path schemaPath) throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newSchema(new StreamSource(schemaPath.toFile()));
    }

    private static Element child(Element parent, String tag) {
        for (Element child : children(parent, tag)) {
            return child;
        }
        throw new IllegalArgumentException("Missing <" + tag + "> under <" + parent.getTagName() + ">");
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && ((Element) node).getTagName().equals(tag)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + name + " on <" + element.getTagName() + ">");
        }
        return value;
    }

    private static int parseInt(Element element, String attribute) {
        try {
            return Integer.parseInt(requiredAttribute(element, attribute));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + attribute + " on <" + element.getTagName() + ">", e);
        }
    }

    private static void requireTag(Element element, String expected) {
        if (!element.getTagName().equals(expected)) {
            throw new IllegalArgumentException("Expected <" + expected + "> but found <" + element.getTagName() + ">");
        }
    }

    private static <T> void putUnique(Map<String, T> values, String key, T value) {
        if (values.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Duplicate catalog id " + key);
        }
    }

    public record Program(String id, String source) implements Comparable<Program> {
        @Override
        public int compareTo(Program other) {
            return id.compareTo(other.id);
        }
    }

    /** The complete JLS section list of one category; coverage is measured against it. */
    public record Scope(String id, String title, List<String> jlsSections) implements Comparable<Scope> {
        public Scope {
            jlsSections = List.copyOf(jlsSections);
        }

        @Override
        public int compareTo(Scope other) {
            return id.compareTo(other.id);
        }
    }

    /**
     * An area's JLS sections: {@code jlsSections} are covered by its programs; {@code skippedSections} maps each
     * skipped section to its reason.
     */
    public record Area(String id, String title, List<String> jlsSections, Map<String, String> skippedSections,
                       List<String> programIds, Map<String, String> backendStatuses) implements Comparable<Area> {
        public Area {
            jlsSections = List.copyOf(jlsSections);
            skippedSections = Map.copyOf(skippedSections);
            programIds = List.copyOf(programIds);
            backendStatuses = Map.copyOf(backendStatuses);
        }

        @Override
        public int compareTo(Area other) {
            return id.compareTo(other.id);
        }
    }

    /** Counts of one scope's sections: covered, skipped as a gap, skipped for another category, or out of scope. */
    public record Coverage(String scopeId, int total, int covered, int gaps, int otherCategory, int outOfScope) {
        public double percentCovered() {
            return total == 0 ? 0.0 : 100.0 * covered / total;
        }

        public String summary() {
            return String.format("%s: %d/%d JLS sections covered (%.1f%%); skipped: %d gaps, %d other categories, "
                    + "%d out of scope", scopeId, covered, total, percentCovered(), gaps, otherCategory, outOfScope);
        }
    }
}
