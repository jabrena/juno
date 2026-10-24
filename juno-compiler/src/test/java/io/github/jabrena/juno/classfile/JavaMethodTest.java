package io.github.jabrena.juno.classfile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Java method value semantics")
class JavaMethodTest {
    @Test
    @DisplayName("Compares bytecode arrays by content")
    void shouldCompareBytecodeByContent() {
        // Given
        JavaMethod first = method(new byte[] {1, 2, 3});
        JavaMethod second = method(new byte[] {1, 2, 3});

        // Then
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("Rejects identity and unrelated-object comparisons")
    void shouldHandleIdentityAndUnrelatedObjectComparisons() {
        // Given
        JavaMethod method = method(new byte[] {1, 2, 3});

        // When & Then
        assertThat(method.equals(method)).isTrue();
        assertThat(method.equals("not a method")).isFalse();
    }

    @Test
    @DisplayName("Compares every method value when checking equality")
    void shouldRejectMethodsWithDifferentValues() {
        // Given
        JavaMethod method = method(new byte[] {1, 2, 3});

        // When & Then
        assertThat(method).isNotEqualTo(new JavaMethod("other.Owner", 0, "run", "()V", 1, 2,
                new byte[] {1, 2, 3}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 1, "run", "()V", 1, 2,
                new byte[] {1, 2, 3}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 0, "other", "()V", 1, 2,
                new byte[] {1, 2, 3}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 0, "run", "(I)V", 1, 2,
                new byte[] {1, 2, 3}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 0, "run", "()V", 2, 2,
                new byte[] {1, 2, 3}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 0, "run", "()V", 1, 3,
                new byte[] {1, 2, 3}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 0, "run", "()V", 1, 2,
                new byte[] {4, 5, 6}));
        assertThat(method).isNotEqualTo(new JavaMethod("demo.Owner", 0, "run", "()V", 1, 2,
                new byte[] {1, 2, 3}, java.util.List.of(new ExceptionHandler(0, 1, 2, "java/lang/Exception"))));
    }

    @Test
    @DisplayName("Shows bytecode contents in its string form")
    void shouldShowBytecodeContentsInToString() {
        // Given
        JavaMethod method = method(new byte[] {1, 2, 3});

        // Then
        assertThat(method).hasToString("JavaMethod[owner=demo.Owner, accessFlags=0, name=run, descriptor=()V, "
                + "maxStack=1, maxLocals=2, code=[1, 2, 3], exceptionHandlers=[]]");
    }

    private JavaMethod method(byte[] code) {
        return new JavaMethod("demo.Owner", 0, "run", "()V", 1, 2, code);
    }
}
