package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.LinkedMethod;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.lowering.StackValueOps.*;

/** {@code System.getenv} and LED-matrix draw-text call recognition/lowering, split out of {@link InvokeLowering}. */
final class MiscIntrinsicLowering {
    private static final MethodRef DRAW_TEXT_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawText", "([[ZLjava/lang/String;II)V");
    private static final MethodRef DRAW_CHAR_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawChar", "([[ZIII)V");
    /** {@code io.github.jabrena.juno.api.led.LedMatrixFontAscii.GLYPH_WIDTH + 1} (a 5-wide glyph plus a
     * 1-column gap), kept as a literal so codegen doesn't depend on a specific font's internals; keep
     * the two in sync if the font's geometry ever changes. */
    private static final int DRAW_TEXT_CHAR_SPACING = 6;

    private MiscIntrinsicLowering() {
    }

    static boolean isCompileTimeGetenv(MethodRef called) {
        return called.owner().equals("java/lang/System") && called.name().equals("getenv")
                && called.descriptor().equals("(Ljava/lang/String;)Ljava/lang/String;");
    }

    static Lowered lowerCompileTimeGetenv(LinkedMethod linked, Instruction instruction,
                                          List<IrInstruction> instructions, int stackBase, int depth,
                                          int nextValueId, ValueTracking tracking) {
        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = popped.nextValueId();
        String variableName = tracking.knownString(popped.value());
        if (variableName == null) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": System.getenv requires a compile-time string literal "
                    + "environment-variable name");
        }
        String value = System.getenv(variableName);
        if (value == null) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": environment variable " + variableName + " is not set; Juno "
                    + "resolves System.getenv(...) at compile time, so it must be set wherever `juno compile` runs");
        }
        nextValueId = pushStringConst(instructions, stackBase, depth, nextValueId, value, tracking);
        depth++;
        return new Lowered(nextValueId, depth);
    }

    static boolean isDrawTextCall(MethodRef called) {
        return called.equals(DRAW_TEXT_METHOD);
    }

    static Lowered lowerDrawText(LinkedMethod linked, Instruction instruction, List<IrInstruction> instructions,
                                 int stackBase, int depth, int nextValueId, ValueTracking tracking) {
        Popped originY = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = originY.nextValueId();
        Popped originX = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = originX.nextValueId();
        Popped text = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = text.nextValueId();
        String message = tracking.knownString(text.value());
        if (message == null) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": " + DRAW_TEXT_METHOD.displayName() + " requires a compile-time "
                    + "string literal text argument (Juno has no heap for a runtime String value)");
        }
        Popped frame = pop(instructions, stackBase, --depth, nextValueId, tracking);
        nextValueId = frame.nextValueId();

        for (int index = 0; index < message.length(); index++) {
            Value charValue = Value.int32(nextValueId++);
            instructions.add(new IrInstruction.Const(charValue, message.charAt(index)));
            Value xValue = originX.value();
            if (index > 0) {
                Value columnOffset = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.Const(columnOffset, index * DRAW_TEXT_CHAR_SPACING));
                xValue = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.Binary(xValue, BinaryOp.ADD, originX.value(), columnOffset));
            }
            instructions.add(new IrInstruction.Call(Optional.empty(), DRAW_CHAR_METHOD,
                    List.of(frame.value(), charValue, xValue, originY.value())));
        }
        return new Lowered(nextValueId, depth);
    }
}
