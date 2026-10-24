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
