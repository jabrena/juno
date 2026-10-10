package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Array initializers as compilers emit them: javac stores every element, the Eclipse compiler only non-zero ones. */
class TableBytecodeTest {
    private static final int BIPUSH = 16;
    private static final int NEWARRAY = 188;
    private static final int T_SHORT = 9;
    private static final int DUP = 89;
    private static final int SASTORE = 86;
    private static final int PUTSTATIC = 179;

    @Test
    void readsAnInitializerThatLeavesOutItsZeroElements() {
        // static final short[] T = {0, 7, 0, 9}; as the Eclipse compiler emits it: no stores for the zeros.
        TableBytecode.Initializer initializer = TableBytecode.initializer(List.of(
                instruction(BIPUSH, 4), instruction(NEWARRAY, T_SHORT),
                instruction(DUP, 0), instruction(BIPUSH, 1), instruction(BIPUSH, 7), instruction(SASTORE, 0),
                instruction(DUP, 0), instruction(BIPUSH, 3), instruction(BIPUSH, 9), instruction(SASTORE, 0),
                instruction(PUTSTATIC, 1)), null, 0);

        assertThat(initializer).isNotNull();
        assertThat(initializer.values()).containsExactly(0, 7, 0, 9);
        assertThat(initializer.putstatic()).isEqualTo(10);
    }

    @Test
    void rejectsAnInitializerThatStoresOneIndexTwice() {
        assertThat(TableBytecode.initializer(List.of(
                instruction(BIPUSH, 2), instruction(NEWARRAY, T_SHORT),
                instruction(DUP, 0), instruction(BIPUSH, 1), instruction(BIPUSH, 7), instruction(SASTORE, 0),
                instruction(DUP, 0), instruction(BIPUSH, 1), instruction(BIPUSH, 8), instruction(SASTORE, 0),
                instruction(PUTSTATIC, 1)), null, 0)).isNull();
    }

    private static Instruction instruction(int opcode, int operand) {
        return new Instruction(0, opcode, operand, 0);
    }
}
