package io.github.jabrena.juno.linker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorTest {
    @Test
    void acceptsFloatDoubleAndLongParametersAndResults() {
        assertTrue(Descriptor.parse("(FIF)F").usesOnlyV01Types());
        assertTrue(Descriptor.parse("([FI)[F").usesOnlyV01Types());
        assertTrue(Descriptor.parse("(DID)D").usesOnlyV01Types());
        assertTrue(Descriptor.parse("([DI)[D").usesOnlyV01Types());
        assertTrue(Descriptor.parse("(J)F").usesOnlyV01Types());
        assertTrue(Descriptor.parse("(F)J").usesOnlyV01Types());
        assertTrue(Descriptor.parse("([JI)[J").usesOnlyV01Types());
    }
}
