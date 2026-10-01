package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LambdaSite;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.lowering.StackValueOps.pop;
import static io.github.jabrena.juno.lowering.StackValueOps.storeDoubleToStack;
import static io.github.jabrena.juno.lowering.StackValueOps.storeToStack;
import static io.github.jabrena.juno.lowering.StackValueOps.storeWideToStack;

/** Materializes lambda references/closures and adapts their interface calls to implementation calls. */
final class LambdaLowering {
    private LambdaLowering() {
    }

    static Lowered lowerFactory(LambdaSite site, List<IrInstruction> instructions, int stackBase, int depth,
                                int nextValueId, ValueTracking tracking) {
        Value[] captures = new Value[site.captureTypes().size()];
        for (int index = captures.length - 1; index >= 0; index--) {
            Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
            nextValueId = popped.nextValueId();
            captures[index] = popped.value();
        }
        Value target = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.LambdaCreate(target, site, List.of(captures)));
        storeToStack(instructions, stackBase, depth, target, tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    static Lowered lowerCall(LinkedMethod linked, Instruction instruction, LambdaSite site,
                             List<IrInstruction> instructions, int stackBase, int depth,
                             int nextValueId, ValueTracking tracking) {
        Descriptor descriptor = Descriptor.parse(site.interfaceMethod().descriptor());
        CallArguments popped = InvokeLowering.popCallArguments(linked, instruction, site.interfaceMethod(),
                descriptor, Optional.empty(), instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = popped.nextValueId();
        depth = popped.depth();
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();

        Value target = resultValue(descriptor, nextValueId);
        if (target != null) {
            nextValueId++;
        }
        List<Value> arguments = new ArrayList<>();
        arguments.add(receiver.value());
        for (Value argument : popped.arguments()) {
            arguments.add(argument);
        }
        instructions.add(new IrInstruction.LambdaCall(Optional.ofNullable(target), site, arguments));
        if (target == null) {
            return new Lowered(nextValueId, depth);
        }
        if (target.type() == JunoType.INT64) {
            Value low = Value.int32(nextValueId++);
            Value high = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.UnpackLong(low, high, target));
            storeWideToStack(instructions, stackBase, depth, low, high, tracking);
        } else if (target.type() == JunoType.FLOAT64) {
            storeDoubleToStack(instructions, stackBase, depth, target, tracking);
        } else {
            storeToStack(instructions, stackBase, depth, target, tracking);
        }
        return new Lowered(nextValueId, depth + target.type().jvmSlots());
    }

    private static Value resultValue(Descriptor descriptor, int nextValueId) {
        if (descriptor.returnsVoid()) {
            return null;
        }
        if (Descriptor.isLong(descriptor.returnType())) {
            return Value.int64(nextValueId);
        }
        if (Descriptor.isDouble(descriptor.returnType())) {
            return Value.float64(nextValueId);
        }
        return Descriptor.isFloat(descriptor.returnType())
                ? Value.float32(nextValueId) : Value.int32(nextValueId);
    }
}
