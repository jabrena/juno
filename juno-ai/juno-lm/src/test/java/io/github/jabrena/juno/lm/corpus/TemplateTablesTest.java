package io.github.jabrena.juno.lm.corpus;

import io.github.jabrena.juno.lm.JlmFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Template tables")
class TemplateTablesTest {
    private static final String TEMPLATES = """
            # comment
            chip => Which chip is in the {s}? | {s} chip?
            """;

    @TempDir
    private Path directory;

    @Test
    @DisplayName("Expands every template with every subject name into one fact per row")
    void shouldExpandTemplatesWithEverySubjectName() {
        // Given
        TemplateTables tables = new TemplateTables("templates.tpl", TEMPLATES);

        // When
        List<Fact> facts = tables.expand("boards.tsv", "chip\tNano ESP32|Nano\tAn ESP32-S3.\n\n# skip\n");

        // Then
        assertThat(facts).containsExactly(new Fact(List.of("Which chip is in the Nano ESP32?", "Nano ESP32 chip?",
                "Which chip is in the Nano?", "Nano chip?"), List.of("An ESP32-S3.")));
    }

    @Test
    @DisplayName("Rejects malformed templates and rows, naming the file and line")
    void shouldRejectMalformedTemplatesAndRows() {
        // Given
        TemplateTables tables = new TemplateTables("templates.tpl", TEMPLATES);

        // When & Then
        assertThatThrownBy(() -> new TemplateTables("t.tpl", "chip => no subject here"))
                .isInstanceOf(JlmFormatException.class).hasMessageStartingWith("t.tpl:1:");
        assertThatThrownBy(() -> new TemplateTables("t.tpl", " => {s}?")).isInstanceOf(JlmFormatException.class);
        assertThatThrownBy(() -> tables.expand("b.tsv", "clock\tNano\t240 MHz."))
                .isInstanceOf(JlmFormatException.class).hasMessageStartingWith("b.tsv:1:")
                .hasMessageContaining("relation that has templates");
        assertThatThrownBy(() -> tables.expand("b.tsv", "chip\tNano"))
                .isInstanceOf(JlmFormatException.class);
        assertThatThrownBy(() -> tables.expand("b.tsv", "chip\t" + "x".repeat(70) + "\tAn ESP32."))
                .isInstanceOf(JlmFormatException.class).hasMessageStartingWith("b.tsv:1:");
    }

    @Test
    @DisplayName("A facts directory with templates.tpl reads its .tsv tables after its .txt facts")
    void shouldReadTablesFromAFactsDirectory() throws Exception {
        // Given
        Files.writeString(directory.resolve("facts.txt"), "Hello => Hi!\n");
        Files.writeString(directory.resolve("templates.tpl"), TEMPLATES);
        Files.writeString(directory.resolve("boards.tsv"), "chip\tUNO R3\tAn ATmega328P.\n");

        // When
        List<Fact> facts = new FactsReader().read(directory);

        // Then
        assertThat(facts).extracting(Fact::primaryAnswer).containsExactly("Hi!", "An ATmega328P.");
        assertThat(facts.get(1).questions()).containsExactly("Which chip is in the UNO R3?", "UNO R3 chip?");
    }

    @Test
    @DisplayName("Ignores .tsv tables in a directory without templates")
    void shouldIgnoreTablesWithoutTemplates() throws Exception {
        // Given
        Files.writeString(directory.resolve("facts.txt"), "Hello => Hi!\n");
        Files.writeString(directory.resolve("boards.tsv"), "chip\tUNO R3\tAn ATmega328P.\n");

        // When & Then
        assertThat(new FactsReader().read(directory)).extracting(Fact::primaryAnswer).containsExactly("Hi!");
    }
}
