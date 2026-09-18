package io.github.jabrena.juno.linker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorTest {
    @Test
    void acceptsFloatParametersAndResultsButStillRejectsDoubleAndLong() {
        assertTrue(Descriptor.parse("(FIF)F").usesOnlyV01Types());
        assertFalse(Descriptor.parse("(D)F").usesOnlyV01Types());
        assertFalse(Descriptor.parse("(F)D").usesOnlyV01Types());
        assertFalse(Descriptor.parse("(J)F").usesOnlyV01Types());
        assertFalse(Descriptor.parse("(F)J").usesOnlyV01Types());
    }
}
