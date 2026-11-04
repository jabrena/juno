///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Checks that every entry of a backend job's Failsafe JUnit XML reports names its program and target board.
 *
 * <p>Usage: {@code jbang ConformanceReportCheck.java <failsafe-reports directory>}, or, with no JBang installed,
 * {@code java ConformanceReportCheck.java <failsafe-reports directory>}.
 *
 * <p>An entry passes when its name holds a program ({@code demo.<Name>} or {@code <Name>}, which JUnit quotes as a
 * String argument) followed by a board constant in parentheses, as the runners' display names do
 * ({@code conformance "demo.IntegralArithmetic" (UNO_R4_WIFI) ["integral-arithmetic"]},
 * {@code "IntMath" (UNO_R3_WIFI_REV2)}). An index alone
 * ({@code matches(String, Board)[7]}) fails, because the report must identify the run without the build log. A
 * directory without reports passes: whether the job produced any is the upload step's concern, not this check's.
 */
public final class ConformanceReportCheck {
    private static final Pattern ENTRY = Pattern.compile("\"?(demo\\.)?[A-Z][A-Za-z0-9_]*\"? \\([A-Z0-9_]+\\)");

    private ConformanceReportCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ConformanceReportCheck <failsafe-reports directory>");
            System.exit(2);
        }
        int checked = 0;
        List<String> unnamed = new ArrayList<>();
        for (Path report : reports(Path.of(args[0]))) {
            NodeList cases = parse(report).getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                checked++;
                String name = ((Element) cases.item(i)).getAttribute("name");
                if (!ENTRY.matcher(name).find()) {
                    unnamed.add(report.getFileName() + ": " + name);
                }
            }
        }
        unnamed.forEach(entry -> System.out.println("::error::JUnit entry does not name its program and board: " + entry));
        System.out.printf("%d JUnit entries checked, %d without a program and board%n", checked, unnamed.size());
        System.exit(unnamed.isEmpty() ? 0 : 1);
    }

    private static List<Path> reports(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("TEST-") && name.endsWith(".xml");
            }).sorted().toList();
        }
    }

    private static Element parse(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(report.toFile()).getDocumentElement();
    }
}
