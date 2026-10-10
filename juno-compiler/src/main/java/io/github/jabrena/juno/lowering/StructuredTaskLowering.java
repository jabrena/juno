package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.StructuredTaskSupport;

import java.util.List;

import static io.github.jabrena.juno.lowering.StackValueOps.storeToStack;

/** Bytecode-stack adaptations specific to JDK 27 structured-task joiners and interface calls. */
final class StructuredTaskLowering {
    private StructuredTaskLowering() {
    }

    /** Materializes a built-in {@code Joiner} as a small runtime policy token. */
    static Lowered lowerJoinerFactory(MethodRef called, List<IrInstruction> instructions, int stackBase,
                                      int depth, int nextValueId, ValueTracking tracking) {
        Integer policy = StructuredTaskSupport.joinerPolicy(called);
        if (policy == null) {
            throw new IllegalArgumentException("Not a supported Joiner factory: " + called.displayName());
        }
        Value joiner = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Const(joiner, policy));
        storeToStack(instructions, stackBase, depth, joiner, tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    static Lowered lowerInterface(LinkedMethod linked, Instruction instruction, MethodRef called,
                                  List<IrInstruction> instructions, int stackBase, int depth,
                                  int nextValueId, ValueTracking tracking) {
        return InvokeLowering.lowerResolvedCall(linked, instruction, called, true, List.of(), instructions,
                stackBase, depth, nextValueId, tracking);
    }
}
