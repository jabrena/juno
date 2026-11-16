package io.github.jabrena.juno.backend.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A runtime String is an arena block of exactly its length: no shared pool, no length limit, no overwrite. */
@DisplayName("Runtime Strings in the arena")
class RuntimeStringsTest {
    private final ShimLibraries shimLibraries = new ShimLibraries();

    @Test
    @DisplayName("String.valueOf copies its digits into an arena String of exactly their length")
    void shouldCopyValueOfIntoAnExactArenaString() {
        // Given
        String strings = shimLibraries.runtimeStrings();

        // When & Then
        assertThat(strings).contains("juno_alloc(length + 1u, 1u)", "return juno_string_copy(buffer, index);")
                .doesNotContain("juno_string_slots", "juno_string_overflow");
    }

    @Test
    @DisplayName("A concatenation always takes a measured arena block, so appends never check a slot size")
    void shouldBuildEveryConcatenationInAMeasuredArenaBlock() {
        // Given
        String operations = shimLibraries.runtimeStringOperations();
        String sized = operations.substring(operations.indexOf("extern \"C\" int32_t juno_string_concat_new_sized"));

        // When & Then
        assertThat(sized.substring(0, sized.indexOf("\n}\n")))
                .contains("juno_alloc(static_cast<uint32_t>(length) + 1u, 1u)");
        assertThat(operations).doesNotContain("JUNO_STRING_SLOT_SIZE", "juno_string_overflow",
                "juno_string_concat_new()");
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
