package io.github.jabrena.juno.tny2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TNY2 vocabulary")
class Tny2VocabularyTest {
    private final Tny2Vocabulary vocabulary = Tny2Vocabulary.ASCII;

    @Test
    @DisplayName("Maps printable ASCII to tokens 2 to 96 and back")
    void shouldMapPrintableAsciiToTokensAndBack() {
        // When & Then
        assertThat(vocabulary.token(' ')).isEqualTo(2);
        assertThat(vocabulary.token('~')).isEqualTo(96);
        assertThat(vocabulary.tokens("Hi")).containsExactly('H' - 30, 'i' - 30);
        for (char c = ' '; c <= '~'; c++) {
            assertThat(vocabulary.character(vocabulary.token(c))).isEqualTo(c);
        }
    }

    @Test
    @DisplayName("Treats characters outside printable ASCII as spaces")
    void shouldTreatCharactersOutsidePrintableAsciiAsSpaces() {
        // When & Then
        assertThat(vocabulary.token('\u001f')).isEqualTo(2);
        assertThat(vocabulary.token('\u007f')).isEqualTo(2);
        assertThat(vocabulary.token('ñ')).isEqualTo(2);
        assertThat(vocabulary.isPrintable('~')).isTrue();
        assertThat(vocabulary.isPrintable('\u007f')).isFalse();
    }

    @Test
    @DisplayName("Rejects the PAD and END tokens and tokens past the vocabulary as characters")
    void shouldRejectTokensThatAreNotCharacters() {
        // When & Then
        assertThatThrownBy(() -> vocabulary.character(Tny2Vocabulary.PAD)).isInstanceOf(Tny2FormatException.class);
        assertThatThrownBy(() -> vocabulary.character(Tny2Vocabulary.END)).isInstanceOf(Tny2FormatException.class);
        assertThatThrownBy(() -> vocabulary.character(Tny2Vocabulary.SIZE))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("97");
    }

    @Test
    @DisplayName("Normalizes a question as the board does: lower case, no ? ! , ; or quotes, single spaces")
    void shouldNormalizeAQuestionAsTheBoardDoes() {
        // When & Then
        assertThat(vocabulary.question("Hola, \u00bfqu\u00e9 tal?  ")).isEqualTo("hola qu tal");
        assertThat(vocabulary.question("  What  is   JUNO?!")).isEqualTo("what is juno");
        assertThat(vocabulary.question("Say \"hi\"; now")).isEqualTo("say hi now");
        assertThat(vocabulary.question("juno:flash -Djuno.port=/dev/x")).isEqualTo("juno:flash -djuno.port=/dev/x");
        assertThat(vocabulary.question("a".repeat(80))).hasSize(Tny2Vocabulary.MAX_QUESTION);
        assertThat(vocabulary.question(" \t ")).isEmpty();
        assertThat(vocabulary.question("??")).isEmpty();
    }
}
