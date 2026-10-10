package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.linker.StringConcatSite;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.lowering.StackValueOps.storeToStack;

/** Lowers a validated StringConcatFactory recipe into bounded runtime-string append calls. */
final class StringConcatLowering {
    private static final String STRING = "Ljava/lang/String;";
    private static final MethodRef CONCAT = new MethodRef("java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants", "()Ljava/lang/String;");

    private StringConcatLowering() {
    }

    static Lowered lower(LinkedMethod linked, Instruction instruction, StringConcatSite site,
                         List<IrInstruction> instructions, int stackBase, int depth,
                         int nextValueId, ValueTracking tracking) {
        Descriptor descriptor = new Descriptor(site.argumentTypes(), STRING);
        CallArguments popped = InvokeLowering.popCallArguments(linked, instruction, CONCAT, descriptor,
                Optional.empty(), instructions, stackBase, depth, nextValueId, tracking);
        nextValueId = popped.nextValueId();
        depth = popped.depth();

        Value handle = Value.int32(nextValueId++);
        instructions.add(new IrInstruction.IntrinsicCall(Optional.of(handle), Intrinsic.STRING_CONCAT_NEW,
                Optional.empty(), List.of(), List.of()));
        for (StringConcatSite.Part part : site.parts()) {
            if (part instanceof StringConcatSite.LiteralPart literal) {
                Value text = Value.int32(nextValueId++);
                instructions.add(new IrInstruction.StringConst(text, literal.value()));
                append(instructions, handle, Intrinsic.STRING_CONCAT_APPEND_STRING, text);
            } else if (part instanceof StringConcatSite.ArgumentPart argument) {
                append(instructions, handle, appendIntrinsic(argument.type()), popped.arguments()[argument.index()]);
            }
        }
        storeToStack(instructions, stackBase, depth, handle, tracking);
        return new Lowered(nextValueId, depth + 1);
    }

    private static Intrinsic appendIntrinsic(String type) {
        return switch (type) {
            case STRING -> Intrinsic.STRING_CONCAT_APPEND_STRING;
            case "Z" -> Intrinsic.STRING_CONCAT_APPEND_BOOLEAN;
            case "C" -> Intrinsic.STRING_CONCAT_APPEND_CHAR;
            case "B", "S", "I" -> Intrinsic.STRING_CONCAT_APPEND_INT;
            case "J" -> Intrinsic.STRING_CONCAT_APPEND_LONG;
            case "F" -> Intrinsic.STRING_CONCAT_APPEND_FLOAT;
            case "D" -> Intrinsic.STRING_CONCAT_APPEND_DOUBLE;
            default -> throw new IllegalArgumentException("Unsupported validated string-concat type " + type);
        };
    }

    private static void append(List<IrInstruction> instructions, Value handle, Intrinsic intrinsic, Value value) {
        instructions.add(new IrInstruction.IntrinsicCall(Optional.empty(), intrinsic, Optional.of(handle),
                List.of(value), List.of()));
    }
}
