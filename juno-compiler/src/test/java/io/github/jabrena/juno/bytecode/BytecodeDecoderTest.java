package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.JavaMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Bytecode decoder")
class BytecodeDecoderTest {
    private final BytecodeDecoder decoder = new BytecodeDecoder();

    @Test
    @DisplayName("Rejects a method without a Code attribute")
    void shouldRejectMethodWithoutBytecode() {
        // Given
        JavaMethod method = new JavaMethod("demo.MissingCode", JavaMethod.ACC_ABSTRACT, "run", "()V", 0, 0, null);

        // When & Then
        assertThatThrownBy(() -> decoder.decode(method))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("method has no bytecode");
    }

    @Test
    @DisplayName("Decodes a method with bytecode")
    void shouldDecodeAvailableBytecode() {
        // Given
        JavaMethod method = new JavaMethod("demo.Return", 0, "run", "()V", 0, 0, new byte[] {0, (byte) 177});

        // When
        List<Instruction> instructions = decoder.decode(method);

        // Then
        assertThat(instructions).extracting(Instruction::opcode).containsExactly(0, 177);
    }
}
