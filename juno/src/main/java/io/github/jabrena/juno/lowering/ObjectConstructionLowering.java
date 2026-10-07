package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.linker.AtomicSupport;
import io.github.jabrena.juno.linker.BigNumberSupport;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.LockSupport;

import java.util.List;

/**
 * The {@code new X(...)} constructors Juno turns into one runtime handle (a {@code StringBuilder}, {@code Thread},
 * {@code ReentrantLock}, {@code Properties}, {@code BigInteger}, {@code BigDecimal} or {@code MathContext}),
 * dispatched from {@link InvokeLowering#lowerInvokeSpecial}.
 */
final class ObjectConstructionLowering {
    private ObjectConstructionLowering() {
    }

    static boolean handles(MethodRef called) {
        return BigNumberSupport.isConstruction(called)
                || InvokeLowering.isStringBuilderConstruction(called)
                || called.equals(LockSupport.CONSTRUCTOR)
                || AtomicSupport.isConstruction(called)
                || InvokeLowering.isPropertiesConstruction(called);
    }

    static Lowered lower(LinkedMethod linked, Instruction instruction, MethodRef called,
                         List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                         ValueTracking tracking) {
        if (BigNumberSupport.isConstruction(called)) {
            return BigNumberLowering.lowerConstruction(linked, instruction, called, instructions, stackBase, depth,
                    nextValueId, tracking);
        }
        if (InvokeLowering.isStringBuilderConstruction(called)) {
            return InvokeLowering.lowerStringBuilderConstruction(instructions, stackBase, depth, nextValueId,
                    tracking);
        }
        if (AtomicSupport.isConstruction(called)) {
            return BigNumberLowering.lowerConstruction(linked, instruction, called, instructions, stackBase, depth,
                    nextValueId, tracking);
        }
        if (called.equals(LockSupport.CONSTRUCTOR)) {
            return InvokeLowering.lowerReentrantLockConstruction(instructions, stackBase, depth, nextValueId,
                    tracking);
        }
        return InvokeLowering.lowerPropertiesConstruction(instructions, stackBase, depth, nextValueId, tracking);
    }
}
