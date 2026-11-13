package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the five board-specific facades removed: {@code MCU} is the only onboard-service facade, so no repository
 * source or maintained document may name the old types. The migration guide is the one place that maps them.
 */
@DisplayName("Removed board facades stay removed")
class RemovedFacadeReferencesTest {
    private static final Pattern REMOVED_FACADE = Pattern.compile(
            "\\b(ArduinoUno|ArduinoNano|RaspberryPico2|Esp32Cam|M5StackCardputer)\\b");
    private static final Path MIGRATION_GUIDE = Path.of("juno-site/src/main/resources/content/migrate-to-mcu.md");
    private static final String SELF = "RemovedFacadeReferencesTest.java";

    @Test
    @DisplayName("No Java source, test fixture, example or maintained document references a removed facade")
    void shouldContainNoReferencesToRemovedFacadesOutsideTheMigrationGuide() throws IOException {
        // Given
        Path root = repositoryRoot();

        // When
        List<String> offenders = new ArrayList<>();
        for (Path file : maintainedFiles(root)) {
            if (file.getFileName().toString().equals(SELF) || file.endsWith(MIGRATION_GUIDE)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (REMOVED_FACADE.matcher(lines.get(i)).find()) {
                    offenders.add(root.relativize(file) + ":" + (i + 1));
                }
            }
        }

        // Then
        assertThat(offenders).as("references to removed facades").isEmpty();
    }

    @Test
    @DisplayName("The migration guide maps every removed facade to MCU")
    void shouldMapEveryRemovedFacadeInTheMigrationGuide() throws IOException {
        // Given
        String guide = Files.readString(repositoryRoot().resolve(MIGRATION_GUIDE), StandardCharsets.UTF_8);

        // Then
        assertThat(guide).contains("ArduinoUno", "ArduinoNano", "RaspberryPico2", "Esp32Cam", "M5StackCardputer",
                "MCU.getSdCard()", "getSd()");
    }

    private static Path repositoryRoot() {
        // Surefire runs each module from its own directory; the repository root is its parent.
        return Path.of("").toAbsolutePath().getParent();
    }

    private static List<Path> maintainedFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(file -> !file.toString().contains("/target/") && !file.toString().contains("/.git/"))
                    .filter(file -> !file.startsWith(root.resolve("docs")))
                    .filter(file -> !file.startsWith(root.resolve("documentation")))
                    .filter(RemovedFacadeReferencesTest::isMaintainedSource)
                    .forEach(files::add);
        }
        return files;
    }

    private static boolean isMaintainedSource(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".java") || name.equals("README.md") || name.equals("AGENTS.md")
                || (name.endsWith(".md") && file.toString().contains("/juno-site/src/main/resources/content/"));
    }
}
