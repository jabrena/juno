package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.bytecode.Opcodes.DUP;
import static io.github.jabrena.juno.bytecode.Opcodes.POP;
import static io.github.jabrena.juno.bytecode.Opcodes.POP2;

/** {@code pop}, {@code pop2} and {@code dup}. */
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
    }
}
