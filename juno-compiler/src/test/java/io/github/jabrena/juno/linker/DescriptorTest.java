package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@DisplayName("Method descriptor parsing")
class DescriptorTest {

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {
            "(FIF)F", "([FI)[F", "(DID)D", "([DI)[D", "(J)F", "(F)J", "([JI)[J", "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/io/RandomAccessFile;)V", "(Ljava/time/Duration;)V",
            "(Ljava/util/concurrent/StructuredTaskScope$Configuration;)V", "()Ljava/time/Duration;",
            "()Ljava/util/concurrent/StructuredTaskScope$Configuration;"})
    @DisplayName("Accepts float, double, long, and the supported object parameters and results")
    void shouldAcceptFloatDoubleLongAndSupportedObjectTypes(String descriptor) {
        // When & Then
        assertThat(Descriptor.parse(descriptor).usesOnlyV01Types()).isTrue();
    }

    @Test
    @DisplayName("A method with no parameters has an empty parameter list")
    void shouldHaveNoParametersForANoArgumentMethod() {
        // When & Then
        assertThat(Descriptor.parse("()V").parameters()).isEmpty();
    }

    @Test
    @DisplayName("A method with no parameters returns void")
    void shouldReturnVoidForANoArgumentVoidMethod() {
        // When & Then
        assertThat(Descriptor.parse("()V").returnsVoid()).isTrue();
    }

    @Test
    @DisplayName("Accepts a reference type inside the given closed world")
    void shouldAcceptAReferenceTypeInsideTheClosedWorld() {
        // When & Then
        assertThat(Descriptor.parse("(Ldemo/Fixture;)V").usesOnlyV01Types(Set.of("demo/Fixture"))).isTrue();
    }

    @Test
    @DisplayName("Rejects a reference type outside the given closed world")
    void shouldRejectAReferenceTypeOutsideTheClosedWorld() {
        // When & Then
        assertThat(Descriptor.parse("(Ldemo/Fixture;)V").usesOnlyV01Types(Set.of("demo/Other"))).isFalse();
    }

    @Test
    @DisplayName("Rejects a reference type when the closed world is empty")
    void shouldRejectAReferenceTypeWhenTheClosedWorldIsEmpty() {
        // When & Then
        assertThat(Descriptor.parse("(Ldemo/Fixture;)V").usesOnlyV01Types(Set.of())).isFalse();
    }

    @ParameterizedTest(name = "rejects malformed descriptor \"{0}\"")
    @ValueSource(strings = {"", "V", "(I", "()VX", "(Ljava/lang/String)V", "(Q)V", "()"})
    @DisplayName("Rejects malformed method descriptors")
    void shouldRejectMalformedMethodDescriptors(String descriptor) {
        // When & Then
        assertThatThrownBy(() -> Descriptor.parse(descriptor))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Invalid method descriptor");
    }

    @Test
    @DisplayName("Parses a parameter of array-of-array type")
    void shouldKeepTheArrayOfArrayParameter() {
        // When & Then
        assertThat(Descriptor.parse("([[I)[[I").parameters()).containsExactly("[[I");
    }

    @Test
    @DisplayName("Parses a return of array-of-array type")
    void shouldKeepTheArrayOfArrayReturnType() {
        // When & Then
        assertThat(Descriptor.parse("([[I)[[I").returnType()).isEqualTo("[[I");
    }

    @Test
    @DisplayName("Accepts an array-of-array type as using only v0.1 types")
    void shouldAcceptAnArrayOfArrayAsUsingOnlyV01Types() {
        // When & Then
        assertThat(Descriptor.parse("([[I)[[I").usesOnlyV01Types()).isTrue();
    }
}
