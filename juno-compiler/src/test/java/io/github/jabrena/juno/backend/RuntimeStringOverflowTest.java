package io.github.jabrena.juno.backend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A runtime String that outgrows its slot stops the program, but says why on Serial before the bare panic. */
class RuntimeStringOverflowTest {
    @Test
    void theSlotPoolDefinesAnOverflowThatExplainsItselfBeforePanicking() {
        String shim = ShimLibraries.runtimeStringSlots(8);

        assertThat(shim).contains("[[noreturn]] static void juno_string_overflow()")
                .containsPattern("Serial\\.println\\(\"\\[juno-string\\] runtime String longer than 31 characters\"\\);"
                        + "\\s+juno_panic\\(\\);");
    }

    @Test
    void concatenationAndStringBuilderOverflowsGoThroughTheDiagnostic() {
        String operations = ShimLibraries.runtimeStringOperations();
        String builders = ShimLibraries.stringBuilderHelpers();

        assertThat(operations).contains("JUNO_STRING_SLOT_SIZE) juno_string_overflow();")
                .doesNotContain("JUNO_STRING_SLOT_SIZE) juno_panic();");
        assertThat(builders).contains("JUNO_STRING_SLOT_SIZE) juno_string_overflow();")
                .doesNotContain("JUNO_STRING_SLOT_SIZE) juno_panic();");
    }
}
