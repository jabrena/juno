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
    private static final Map<String, Set<String>> REQUIRED_AREAS = Map.of(
            "primitives.literals", Set.of("3.10.1", "3.10.2", "3.10.3", "3.10.4"),
            "primitives.integral-types", Set.of("4.2.1", "4.2.2"),
            "primitives.floating-point-types", Set.of("4.2.3", "4.2.4"),
            "primitives.boolean", Set.of("4.2.5"),
            "primitives.conversions", Set.of("5.1.2", "5.1.3", "5.1.4", "5.2", "5.5", "5.6.1", "5.6.2"),
            "primitives.integral-expressions", Set.of("15.15.1", "15.15.2", "15.15.3", "15.16", "15.17.1",
                    "15.17.2", "15.17.3", "15.18.2", "15.19", "15.20", "15.21", "15.22.1"),
            "primitives.floating-point-expressions", Set.of("15.15.1", "15.15.2", "15.16", "15.17.1",
                    "15.17.2", "15.17.3", "15.18.2", "15.20", "15.21"),
            "primitives.boolean-and-conditional", Set.of("15.7", "15.21", "15.22.2", "15.23", "15.24", "15.25"));

    private final int javaRelease;
    private final Map<String, Program> programs;
    private final Map<String, Area> areas;

    private ConformanceCatalog(int javaRelease, Map<String, Program> programs, Map<String, Area> areas) {
        this.javaRelease = javaRelease;
        this.programs = Map.copyOf(programs);
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
            Map<String, Area> areas = parseAreas(root);
            return new ConformanceCatalog(javaRelease, programs, areas);
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
    }

    public List<Program> programs() {
        return programs.values().stream().sorted().toList();
    }

    public List<Area> areas() {
        return areas.values().stream().sorted().toList();
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

    private static Map<String, Area> parseAreas(Element root) {
        Element areasElement = child(root, "areas");
        Map<String, Area> areas = new LinkedHashMap<>();
        for (Element element : children(areasElement, "area")) {
            String id = requiredAttribute(element, "id");
            String title = requiredAttribute(element, "title");
            List<String> sections = children(element, "jls").stream()
                    .map(section -> requiredAttribute(section, "section"))
                    .distinct().toList();
            List<String> programIds = children(element, "program").stream()
                    .map(program -> requiredAttribute(program, "ref"))
                    .distinct().toList();
            Map<String, String> backends = new LinkedHashMap<>();
            for (Element backend : children(element, "backend")) {
                putUnique(backends, requiredAttribute(backend, "name"), requiredAttribute(backend, "status"));
            }
            putUnique(areas, id, new Area(id, title, sections, programIds, backends));
        }
        return areas;
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

    public record Area(String id, String title, List<String> jlsSections, List<String> programIds,
                       Map<String, String> backendStatuses) implements Comparable<Area> {
        public Area {
            jlsSections = List.copyOf(jlsSections);
            programIds = List.copyOf(programIds);
            backendStatuses = Map.copyOf(backendStatuses);
        }

        @Override
        public int compareTo(Area other) {
            return id.compareTo(other.id);
        }
    }
}
