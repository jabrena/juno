package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.intrinsic.RandomAccessFileMethods;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.List;
import java.util.Optional;

/**
 * {@code new RandomAccessFile(path, "r")} and its buffer reads. Arrays carry no runtime length, so every buffer read
 * passes the shim the array's capacity as a hidden argument: the compile-time length of a local array created with a
 * constant size, or {@code -1} (bounds unchecked, like any other access to such an array) when it is not known here.
 * {@code read(byte[])}/{@code readFully(byte[])} take their whole length from it, so they need a known one.
 */
final class RandomAccessFileLowering {
    private static final int UNKNOWN_CAPACITY = -1;

    private RandomAccessFileLowering() {
    }

    static Lowered lowerConstruction(LinkedMethod linked, Instruction instruction, MethodRef called,
                                     List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                     ValueTracking tracking) {
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        CallArguments popped = InvokeLowering.popCallArguments(linked, instruction, called, descriptor,
                Optional.of(Intrinsic.RAF_OPEN), instructions, stackBase, depth, nextValueId, tracking);
        String mode = popped.literalStrings()[1];
        if (!RandomAccessFileMethods.READ_ONLY_MODE.equals(mode)) {
            throw new CompileException(where(linked, instruction) + ": RandomAccessFile mode \"" + mode
                    + "\" is not supported (only \"r\": SD card files open read-only)");
        }
        nextValueId = popped.nextValueId();
        depth = popped.depth();
        Popped discardedReceiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = discardedReceiver.nextValueId();
        Value handle = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), Intrinsic.RAF_OPEN, Optional.empty(),
                List.of(), List.of(popped.literalStrings()[0])));
        storeToStack(instructions, stackBase, depth - 1, handle, tracking);
        return new Lowered(nextValueId, depth);
    }

    static Lowered lowerBufferRead(LinkedMethod linked, Instruction instruction, MethodRef called,
                                   List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                   ValueTracking tracking) {
        boolean ranged = called.descriptor().startsWith("([BII)");
        Value offset = null;
        Value length = null;
        if (ranged) {
            Popped poppedLength = pop(instructions, stackBase, --depth, nextValueId, tracking);
            Popped poppedOffset = pop(instructions, stackBase, --depth, poppedLength.nextValueId(), tracking);
            nextValueId = poppedOffset.nextValueId();
            length = poppedLength.value();
            offset = poppedOffset.value();
        }
        Popped array = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = array.nextValueId();
        Integer knownLength = tracking.knownLength(array.value());
        if (!ranged && knownLength == null) {
            throw new CompileException(where(linked, instruction) + ": " + called.displayName() + " needs a local "
                    + "array created in this method with a compile-time-constant size; pass (buffer, offset, length) "
                    + "for any other array");
        }
        int capacity = knownLength == null ? UNKNOWN_CAPACITY : knownLength;
        Value capacityValue = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.Const(capacityValue, capacity));
        if (!ranged) {
            offset = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.Const(offset, 0));
            length = capacityValue;
        }
        Popped receiver = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = receiver.nextValueId();
        Intrinsic intrinsic = IntrinsicRegistry.resolve(called).orElseThrow();
        boolean returnsCount = !Descriptor.parse(called.descriptor()).returnsVoid();
        Value count = returnsCount ? Value.int32(nextValueId++) : null;
        instructions.add(new IrInstruction.IntrinsicCall(Optional.ofNullable(count), intrinsic,
                Optional.of(receiver.value()), List.of(array.value(), capacityValue, offset, length), List.of()));
        if (count != null) {
            storeToStack(instructions, stackBase, depth, count, tracking);
            depth++;
        }
        return new Lowered(nextValueId, depth);
    }

    private static String where(LinkedMethod linked, Instruction instruction) {
        return linked.method().reference().displayName() + " at bytecode offset " + instruction.offset();
    }
}
