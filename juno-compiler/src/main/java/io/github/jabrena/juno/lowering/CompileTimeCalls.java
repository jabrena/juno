package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;

/** Calls Juno evaluates while compiling: {@code System.getenv} of a literal, and {@code LedCanvas.drawText}. */
final class CompileTimeCalls {
    private static final MethodRef GETENV = new MethodRef("java/lang/System", "getenv",
            "(Ljava/lang/String;)Ljava/lang/String;");
    private static final MethodRef DRAW_TEXT = new MethodRef("io/github/jabrena/juno/hardware/led/LedCanvas",
            "drawText", "([[ZLjava/lang/String;II)V");
    private static final MethodRef DRAW_CHAR = new MethodRef("io/github/jabrena/juno/hardware/led/LedCanvas",
            "drawChar", "([[ZIII)V");
    /**
     * {@code LedMatrixFontAscii.GLYPH_WIDTH + 1} (a 5-wide glyph plus a 1-column gap), kept as a literal so codegen
     * doesn't depend on a specific font's internals; keep the two in sync if the font's geometry ever changes.
     */
    private static final int CHARACTER_SPACING = 6;

    private final MethodContext context;
    private final OperandStack stack;

    CompileTimeCalls(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
    }

    boolean isGetenv(MethodRef called) {
        return called.equals(GETENV);
    }

    boolean isDrawText(MethodRef called) {
        return called.equals(DRAW_TEXT);
    }

    /**
     * {@code System.getenv("NAME")} reads Juno's build-time environment, wherever {@code juno compile} runs, never
     * the board's; the result becomes a string literal. This keeps e.g. Wi-Fi credentials out of committed source.
     */
    void getenv(Instruction instruction) {
        String name = context.facts().knownString(stack.pop());
        if (name == null) {
            throw new CompileException(context.at(instruction) + ": System.getenv requires a compile-time string "
                    + "literal environment-variable name");
        }
        String value = System.getenv(name);
        if (value == null) {
            throw new CompileException(context.at(instruction) + ": environment variable " + name + " is not set; "
                    + "Juno resolves System.getenv(...) at compile time, so it must be set wherever `juno compile` "
                    + "runs");
        }
        stack.pushString(value);
    }

    /**
     * {@code LedCanvas.drawText(frame, "literal", x, y)} unrolls into one ordinary {@code drawChar} call per
     * character, at {@code x + i * CHARACTER_SPACING}: exactly what writing the calls out by hand would produce.
     */
    void drawText(Instruction instruction) {
        Value originY = stack.pop();
        Value originX = stack.pop();
        String text = context.facts().knownString(stack.pop());
        if (text == null) {
            throw new CompileException(context.at(instruction) + ": " + DRAW_TEXT.displayName() + " requires a "
                    + "compile-time string literal text argument (Juno has no heap for a runtime String value)");
        }
        Value frame = stack.pop();
        FreshValues values = context.values();
        for (int index = 0; index < text.length(); index++) {
            Value character = values.int32();
            context.emit(new IrInstruction.Const(character, text.charAt(index)));
            Value x = originX;
            if (index > 0) {
                Value offset = values.int32();
                context.emit(new IrInstruction.Const(offset, index * CHARACTER_SPACING));
                x = values.int32();
                context.emit(new IrInstruction.Binary(x, BinaryOp.ADD, originX, offset));
            }
            context.emit(new IrInstruction.Call(Optional.empty(), DRAW_CHAR, List.of(frame, character, x, originY)));
        }
    }
}
