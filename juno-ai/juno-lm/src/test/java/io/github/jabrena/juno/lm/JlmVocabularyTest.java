package io.github.jabrena.juno.lm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JLM1 vocabulary")
class JlmVocabularyTest {
    private final JlmVocabulary vocabulary = JlmVocabulary.ASCII;

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
        assertThatThrownBy(() -> vocabulary.character(JlmVocabulary.PAD)).isInstanceOf(JlmFormatException.class);
        assertThatThrownBy(() -> vocabulary.character(JlmVocabulary.END)).isInstanceOf(JlmFormatException.class);
        assertThatThrownBy(() -> vocabulary.character(vocabulary.size()))
                .isInstanceOf(JlmFormatException.class).hasMessageContaining("97");
    }

    @Test
    @DisplayName("Numbers a custom alphabet from token 2 and reads missing characters as the space")
    void shouldNumberACustomAlphabet() {
        // Given
        JlmVocabulary custom = new JlmVocabulary("ab .");

        // When & Then
        assertThat(custom.size()).isEqualTo(6);
        assertThat(custom.tokens("ba.")).containsExactly(3, 2, 5);
        assertThat(custom.token('z')).isEqualTo(4);
        assertThat(custom.token('\u0000')).isEqualTo(4);
        assertThat(custom.character(5)).isEqualTo('.');
        assertThat(custom).isEqualTo(new JlmVocabulary("ab .")).hasSameHashCodeAs(new JlmVocabulary("ab ."))
                .isNotEqualTo(JlmVocabulary.ASCII).isNotEqualTo("ab .");
        assertThat(JlmVocabulary.ASCII.size()).isEqualTo(97);
    }

    @Test
    @DisplayName("Rejects an alphabet without the space, with a repeat or outside printable ASCII")
    void shouldRejectAnAlphabetTheBoardRefuses() {
        // When & Then
        assertThatThrownBy(() -> new JlmVocabulary("abc")).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("space");
        assertThatThrownBy(() -> new JlmVocabulary(" aa")).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("repeats");
        assertThatThrownBy(() -> new JlmVocabulary(" \u00f1")).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("printable");
    }

    @Test
    @DisplayName("Normalizes a question as the board does: lower case, no ? ! , ; or quotes, single spaces")
    void shouldNormalizeAQuestionAsTheBoardDoes() {
        // When & Then
        assertThat(vocabulary.question("Hola, \u00bfqu\u00e9 tal?  ")).isEqualTo("hola qu tal");
        assertThat(vocabulary.question("  What  is   JUNO?!")).isEqualTo("what is juno");
        assertThat(vocabulary.question("Say \"hi\"; now")).isEqualTo("say hi now");
        assertThat(vocabulary.question("juno:flash -Djuno.port=/dev/x")).isEqualTo("juno:flash -djuno.port=/dev/x");
        assertThat(vocabulary.question("a".repeat(80))).hasSize(JlmVocabulary.MAX_QUESTION);
        assertThat(vocabulary.question(" \t ")).isEmpty();
        assertThat(vocabulary.question("??")).isEmpty();
    }
}
