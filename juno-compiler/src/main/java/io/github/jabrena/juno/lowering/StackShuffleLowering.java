package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.bytecode.Opcodes.DUP;
import static io.github.jabrena.juno.bytecode.Opcodes.DUP2;
import static io.github.jabrena.juno.bytecode.Opcodes.DUP2_X1;
import static io.github.jabrena.juno.bytecode.Opcodes.DUP2_X2;
import static io.github.jabrena.juno.bytecode.Opcodes.DUP_X1;
import static io.github.jabrena.juno.bytecode.Opcodes.DUP_X2;
import static io.github.jabrena.juno.bytecode.Opcodes.POP;
import static io.github.jabrena.juno.bytecode.Opcodes.POP2;

/**
 * {@code pop}, {@code pop2} and the {@code dup} family. javac emits {@code dup_x1} for a field post-increment used as
 * a value ({@code a[next++]}), {@code dup2} for compound assignment to an array element ({@code a[i] += x}), and the
 * {@code _x2} and {@code dup2_x1} forms for the same with {@code long} and {@code double} operands.
 */
final class StackShuffleLowering implements OpcodeGroup {
    private final OperandStack stack;

    StackShuffleLowering(OperandStack stack) {
        this.stack = stack;
    }

    @Override
    public void registerOn(OpcodeTable table) {
        table.on(POP, instruction -> stack.drop(1));
        table.on(POP2, instruction -> stack.drop(2));
        table.on(DUP, instruction -> stack.dup());
        table.on(DUP_X1, instruction -> stack.duplicate(1, 1));
        table.on(DUP_X2, instruction -> stack.duplicate(1, 2));
        table.on(DUP2, instruction -> stack.duplicate(2, 0));
        table.on(DUP2_X1, instruction -> stack.duplicate(2, 1));
        table.on(DUP2_X2, instruction -> stack.duplicate(2, 2));
    }
}
