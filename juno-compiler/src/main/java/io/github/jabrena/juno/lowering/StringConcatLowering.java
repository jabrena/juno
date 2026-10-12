package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.StringConcatSite;

import java.util.List;
import java.util.Optional;

/** Lowers a validated {@code StringConcatFactory} recipe into bounded runtime-string append calls. */
final class StringConcatLowering {
    private static final String STRING = "Ljava/lang/String;";
    private static final MethodRef CONCAT = new MethodRef("java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants", "()Ljava/lang/String;");

    private final MethodContext context;
    private final CallLowering calls;

    StringConcatLowering(MethodContext context, CallLowering calls) {
        this.context = context;
        this.calls = calls;
    }

    void concat(Instruction instruction, StringConcatSite site) {
        CallArguments arguments = calls.popArguments(instruction, CONCAT,
                new Descriptor(site.argumentTypes(), STRING), Optional.empty());
        Value builder = context.values().int32();
        context.emit(new IrInstruction.IntrinsicCall(Optional.of(builder), Intrinsic.STRING_CONCAT_NEW,
                Optional.empty(), List.of(), List.of()));
        for (StringConcatSite.Part part : site.parts()) {
            switch (part) {
                case StringConcatSite.LiteralPart literal -> {
                    Value text = context.values().int32();
                    context.emit(new IrInstruction.StringConst(text, literal.value()));
                    append(builder, Intrinsic.STRING_CONCAT_APPEND_STRING, text);
                }
                case StringConcatSite.ArgumentPart argument ->
                        append(builder, appendIntrinsic(argument.type()), arguments.value(argument.index()));
            }
        }
        context.stack().push(builder);
    }

    private Intrinsic appendIntrinsic(String type) {
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

    private void append(Value builder, Intrinsic intrinsic, Value value) {
        context.emit(new IrInstruction.IntrinsicCall(Optional.empty(), intrinsic, Optional.of(builder),
                List.of(value), List.of()));
    }
}
