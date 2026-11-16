package io.github.jabrena.juno.backend.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A runtime String that outgrows its slot stops the program, but says why on Serial before the bare panic. */
@DisplayName("Runtime String overflow diagnostic")
class RuntimeStringOverflowTest {
    private final ShimLibraries shimLibraries = new ShimLibraries();

    @Test
    @DisplayName("The slot pool defines an overflow that explains itself before panicking")
    void shouldDefineAnOverflowThatExplainsItselfBeforePanicking() {
        // Given
        String shim = shimLibraries.runtimeStringSlots(8);

        // When & Then
        assertThat(shim).contains("[[noreturn]] static void juno_string_overflow()")
                .containsPattern("Serial\\.println\\(\"\\[juno-string\\] runtime String longer than 31 characters\"\\);"
                        + "\\s+juno_panic\\(\\);");
    }

    @Test
    @DisplayName("Concatenation overflows go through the diagnostic")
    void shouldRouteConcatenationOverflowsThroughTheDiagnostic() {
        // Given
        String operations = shimLibraries.runtimeStringOperations();

        // When & Then
        assertThat(operations).contains("JUNO_STRING_SLOT_SIZE) juno_string_overflow();")
                .doesNotContain("JUNO_STRING_SLOT_SIZE) juno_panic();");
    }

    @Test
    @DisplayName("StringBuilder.toString() is not limited to one slot")
    void shouldNotLimitStringBuilderResultsToOneSlot() {
        // Given
        String builders = shimLibraries.stringBuilderHelpers();

        // When & Then: the result is allocated to the content's length, so only the builder's capacity bounds it
        assertThat(builders).doesNotContain("JUNO_STRING_SLOT_SIZE", "juno_string_overflow");
    }
}
