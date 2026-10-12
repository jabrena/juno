package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;

import java.util.List;
import java.util.Optional;

/**
 * {@code RandomAccessFile} buffer reads. Arrays carry no runtime length, so every read passes the shim the array's
 * capacity as a hidden argument: the compile-time length of a local array created with a constant size, or
 * {@code -1} (bounds unchecked, like any other access to such an array) when it is not known here.
 * {@code read(byte[])}/{@code readFully(byte[])} take their whole length from it, so they need a known one.
 */
final class RandomAccessFileReads {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private static final int UNKNOWN_CAPACITY = -1;

    private final MethodContext context;
    private final OperandStack stack;

    RandomAccessFileReads(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
    }

    void read(Instruction instruction, MethodRef called) {
        FreshValues values = context.values();
        boolean ranged = called.descriptor().startsWith("([BII)");
        Value length = ranged ? stack.pop() : null;
        Value offset = ranged ? stack.pop() : null;
        Value array = stack.pop();
        Integer knownLength = context.facts().knownLength(array);
        if (!ranged && knownLength == null) {
            throw new CompileException(context.at(instruction) + ": " + called.displayName() + " needs a local "
                    + "array created in this method with a compile-time-constant size; pass (buffer, offset, length) "
                    + "for any other array");
        }
        Value capacity = values.int32();
        context.emit(new IrInstruction.Const(capacity, knownLength == null ? UNKNOWN_CAPACITY : knownLength));
        if (offset == null || length == null) {
            offset = values.int32();
            context.emit(new IrInstruction.Const(offset, 0));
            length = capacity;
        }
        Value receiver = stack.pop();
        Intrinsic intrinsic = intrinsicRegistry.resolve(called).orElseThrow();
        Optional<Value> count = Descriptor.parse(called.descriptor()).returnsVoid()
                ? Optional.empty() : Optional.of(values.int32());
        context.emit(new IrInstruction.IntrinsicCall(count, intrinsic, Optional.of(receiver),
                List.of(array, capacity, offset, length), List.of()));
        count.ifPresent(stack::push);
    }
}
