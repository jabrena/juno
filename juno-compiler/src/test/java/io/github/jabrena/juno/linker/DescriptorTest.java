package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void acceptsAMethodWithNoParameters() {
        Descriptor descriptor = Descriptor.parse("()V");

        assertThat(descriptor.parameters()).isEmpty();
        assertThat(descriptor.returnsVoid()).isTrue();
    }

    @Test
    void acceptsRandomAccessFileDurationAndStructuredTaskConfigurationParameters() {
        assertThat(Descriptor.parse("(Ljava/io/RandomAccessFile;)V").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("(Ljava/time/Duration;)V").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("(Ljava/util/concurrent/StructuredTaskScope$Configuration;)V").usesOnlyV01Types())
                .isTrue();
        assertThat(Descriptor.parse("()Ljava/time/Duration;").usesOnlyV01Types()).isTrue();
        assertThat(Descriptor.parse("()Ljava/util/concurrent/StructuredTaskScope$Configuration;").usesOnlyV01Types())
                .isTrue();
    }

    @Test
    void acceptsAClosedWorldReferenceTypeOnlyWhenItIsInTheGivenSet() {
        String descriptor = "(Ldemo/Fixture;)V";

        assertThat(Descriptor.parse(descriptor).usesOnlyV01Types(Set.of("demo/Fixture"))).isTrue();
        assertThat(Descriptor.parse(descriptor).usesOnlyV01Types(Set.of("demo/Other"))).isFalse();
        assertThat(Descriptor.parse(descriptor).usesOnlyV01Types(Set.of())).isFalse();
    }

    @Test
    void rejectsAnEmptyDescriptor() {
        assertThatThrownBy(() -> Descriptor.parse(""))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void rejectsADescriptorNotStartingWithAnOpenParenthesis() {
        assertThatThrownBy(() -> Descriptor.parse("V"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void rejectsAnUnterminatedParameterList() {
        assertThatThrownBy(() -> Descriptor.parse("(I"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void rejectsTrailingCharactersAfterTheReturnType() {
        assertThatThrownBy(() -> Descriptor.parse("()VX"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void rejectsAnUnterminatedObjectType() {
        assertThatThrownBy(() -> Descriptor.parse("(Ljava/lang/String)V"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void rejectsAnUnknownTypeCharacter() {
        assertThatThrownBy(() -> Descriptor.parse("(Q)V"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void rejectsAMissingReturnType() {
        assertThatThrownBy(() -> Descriptor.parse("()"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    void parsesArrayOfArrayTypes() {
        Descriptor descriptor = Descriptor.parse("([[I)[[I");

        assertThat(descriptor.parameters()).containsExactly("[[I");
        assertThat(descriptor.returnType()).isEqualTo("[[I");
        assertThat(descriptor.usesOnlyV01Types()).isTrue();
    }
}
