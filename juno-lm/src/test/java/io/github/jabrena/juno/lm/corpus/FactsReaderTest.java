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

@DisplayName("Facts reader")
class FactsReaderTest {
    @TempDir
    private Path directory;

    @Test
    @DisplayName("Parses alternatives on both sides and skips blank and comment lines")
    void shouldParseAlternativesAndSkipCommentsAndBlankLines() {
        // Given
        String text = """
                # greetings
                Hello | Hi there |  => Hello! | Hi!

                  What is Juno?=>A compiler.
                """;

        // When
        List<Fact> facts = new FactsReader().parse("facts.txt", text);

        // Then
        assertThat(facts).containsExactly(new Fact(List.of("Hello", "Hi there"), List.of("Hello!", "Hi!")),
                new Fact(List.of("What is Juno?"), List.of("A compiler.")));
    }

    @Test
    @DisplayName("Names the file and line of a malformed fact")
    void shouldNameTheFileAndLineOfAMalformedFact() {
        // Given
        FactsReader reader = new FactsReader();

        // When & Then
        assertThatThrownBy(() -> reader.parse("a.txt", "# ok\nno arrow here"))
                .isInstanceOf(JlmFormatException.class).hasMessageStartingWith("a.txt:2:")
                .hasMessageContaining("question | question => answer | answer");
        assertThatThrownBy(() -> reader.parse("b.txt", "q => a => b")).isInstanceOf(JlmFormatException.class);
        assertThatThrownBy(() -> reader.parse("c.txt", " | => a")).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("c.txt:1: a fact needs");
        assertThatThrownBy(() -> reader.parse("d.txt", "Mañana? => a")).isInstanceOf(JlmFormatException.class)
                .hasMessageStartingWith("d.txt:1:");
    }

    @Test
    @DisplayName("Reads a directory as its .txt files in name order")
    void shouldReadADirectoryAsItsTextFilesInNameOrder() throws Exception {
        // Given
        Files.writeString(directory.resolve("b.txt"), "B => b");
        Files.writeString(directory.resolve("a.txt"), "A => a");
        Files.writeString(directory.resolve("notes.md"), "not => facts");

        // When
        List<Fact> facts = new FactsReader().read(directory);

        // Then
        assertThat(facts).extracting(Fact::primaryQuestion).containsExactly("A", "B");
        assertThat(new FactsReader().read(directory.resolve("b.txt"))).extracting(Fact::primaryAnswer)
                .containsExactly("b");
    }

    @Test
    @DisplayName("Reads the bundled Juno facts")
    void shouldReadTheBundledJunoFacts() throws Exception {
        // When
        List<Fact> facts = new FactsReader().read(Path.of("src/main/resources/lm/facts"));

        // Then
        assertThat(facts).hasSizeGreaterThan(30).anySatisfy(fact -> assertThat(fact.primaryQuestion())
                .isEqualTo("What is Juno?"));
    }
}
