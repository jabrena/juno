package io.github.jabrena.juno.linker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptorTest {
    @Test
    void acceptsFloatDoubleAndLongParametersAndResults() {
        assertThat(Descriptor.parse("(FIF)F").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("([FI)[F").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("(DID)D").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("([DI)[D").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("(J)F").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("(F)J").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("([JI)[J").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("(Ljava/lang/String;)Ljava/lang/String;").usesOnlyV01Types()).isTrue();
    }
}
