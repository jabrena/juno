package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@code new BigInteger(...)}, {@code new BigDecimal(...)} and {@code new MathContext(...)}, split out of {@link InvokeLowering}. */
final class BigNumberLowering {
    private BigNumberLowering() {
    }

    /**
     * {@code new} (opcode 187) already pushed a placeholder and {@code dup}ed it, so this pops the arguments and
     * the duplicate and overwrites the placeholder with the handle the runtime shim builds from them.
     */
    static Lowered lowerConstruction(LinkedMethod linked, Instruction instruction, MethodRef called,
                                     List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                     ValueTracking tracking) {
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Intrinsic intrinsic = IntrinsicRegistry.resolve(called).orElseThrow();
        CallArguments popped = InvokeLowering.popCallArguments(linked, instruction, called, descriptor,
                Optional.of(intrinsic), instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = popped.nextValueId();
        depth = popped.depth();
        List<Value> numericArguments = new ArrayList<>();
        List<String> literalArguments = new ArrayList<>();
        for (int index = 0; index < popped.arguments().length; index++) {
            if (popped.literalStrings()[index] != null) {
                literalArguments.add(popped.literalStrings()[index]);
            } else {
                numericArguments.add(popped.arguments()[index]);
            }
        }
        Popped discardedReceiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = discardedReceiver.nextValueId();
        Value handle = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), intrinsic, Optional.empty(),
                numericArguments, literalArguments));
        storeToStack(instructions, stackBase, depth - 1, handle, tracking);
        return new Lowered(nextValueId, depth);
    }
}
