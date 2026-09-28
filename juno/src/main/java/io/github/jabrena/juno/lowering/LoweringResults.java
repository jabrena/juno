package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;

import java.util.List;

/** Small result types threaded through {@link BytecodeToIr}'s instruction-lowering helpers. */

/** A single popped operand-stack value, plus the next fresh value id. */
record Popped(Value value, int nextValueId) {
}

/**
 * A popped two-slot (long) operand-stack value as its low/high 32-bit halves.
 */
record WidePopped(Value low, Value high, int nextValueId) {
}

/**
 * A popped array/bounds-check length known at compile time, plus the resulting
 * stack depth.
 */
record ConstPop(int value, int depth) {
}

/**
 * The next fresh value id and resulting stack depth after lowering one
 * instruction.
 */
record Lowered(int nextValueId, int depth) {
}

/**
 * Like {@link Lowered}, plus the possibly-split IR block start that a guarded
 * division may advance.
 */
record ArithLowered(int nextValueId, int depth, int irBlockStart) {
}

/**
 * Like {@link Lowered}, plus the block terminator when the opcode ends it
 * (branch/return/throw/switch).
 */
record ControlLowered(int nextValueId, int depth, IrTerminator terminator) {
}

/**
 * The result of lowering one bytecode instruction: {@link Lowered} plus the
 * (rarely touched) IR
 * block start and terminator, so lowerInstruction has one return shape for
 * every opcode group.
 */
record InstructionLowering(int nextValueId, int depth, int irBlockStart, IrTerminator terminator) {
    static InstructionLowering of(Lowered lowered, int irBlockStart) {
        return new InstructionLowering(lowered.nextValueId(), lowered.depth(), irBlockStart, null);
    }

    static InstructionLowering of(ArithLowered lowered) {
        return new InstructionLowering(lowered.nextValueId(), lowered.depth(), lowered.irBlockStart(), null);
    }

    static InstructionLowering of(ControlLowered lowered, int irBlockStart) {
        return new InstructionLowering(lowered.nextValueId(), lowered.depth(), irBlockStart, lowered.terminator());
    }
}

/**
 * {@code lowerCall}\'s popped arguments: each slot is either a numeric
 * {@link Value} or (for a
 * parameter an intrinsic requires/prefers as compile-time text) a
 * {@code literalStrings} entry, never both.
 */
record CallArguments(Value[] arguments, String[] literalStrings, int nextValueId, int depth) {
}

/**
 * The terminator and next fresh value id produced by lowering {@code athrow}.
 */
record Thrown(IrTerminator terminator, int nextValueId) {
}

/**
 * The next fresh value id and (possibly split) IR block start after a guarded
 * division/remainder.
 */
record DivisorGuard(int nextValueId, int blockStart) {
}

/**
 * A compile-time-known record instance: its class and the values assigned to
 * each component.
 */
record RecordInstance(String className, List<Value> fieldValues) {
}
