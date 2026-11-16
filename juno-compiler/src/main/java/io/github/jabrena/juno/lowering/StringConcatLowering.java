package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.StringConcatSite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Lowers a validated {@code StringConcatFactory} recipe into a measured arena {@code String} and its appends. */
final class StringConcatLowering {
    private static final String STRING = "Ljava/lang/String;";
    /** {@code "false"}: a {@code boolean} is counted at its longer spelling rather than measured. */
    private static final int BOOLEAN_BOUND = 5;
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
        context.stack().push(emitConcat(site.parts(), arguments.values()));
    }

    /**
     * Measures the recipe, takes an arena block of exactly that length, and appends every part into it: the
     * literals' lengths and the {@code boolean}/{@code char} bounds are added to the formatted length of every other
     * argument, so the result is a {@code String} of any length that lives as long as something refers to it.
     */
    Value emitConcat(List<StringConcatSite.Part> parts, List<Value> arguments) {
        Value builder = context.values().int32();
        context.emit(new IrInstruction.IntrinsicCall(Optional.of(builder), Intrinsic.STRING_CONCAT_NEW_SIZED,
                Optional.empty(), List.of(measure(parts, arguments)), List.of()));
        for (StringConcatSite.Part part : parts) {
            switch (part) {
                case StringConcatSite.LiteralPart literal -> {
                    Value text = context.values().int32();
                    context.emit(new IrInstruction.StringConst(text, literal.value()));
                    append(builder, Intrinsic.STRING_CONCAT_APPEND_STRING, text);
                }
                case StringConcatSite.ArgumentPart argument ->
                        append(builder, appendIntrinsic(argument.type()), arguments.get(argument.index()));
            }
        }
        return builder;
    }

    /**
     * Emits the result's length: the literals and the {@code boolean} and {@code char} bounds as one constant, plus
     * each other argument's formatted length, added by the runtime one argument at a time.
     */
    private Value measure(List<StringConcatSite.Part> parts, List<Value> arguments) {
        int fixed = 0;
        for (StringConcatSite.Part part : parts) {
            fixed += switch (part) {
                case StringConcatSite.LiteralPart literal -> literalLength(literal);
                case StringConcatSite.ArgumentPart argument -> switch (argument.type()) {
                    case "Z" -> BOOLEAN_BOUND;
                    case "C" -> 1;
                    default -> 0;
                };
            };
        }
        Value length = context.values().int32();
        context.emit(new IrInstruction.Const(length, fixed));
        for (StringConcatSite.Part part : parts) {
            if (part instanceof StringConcatSite.ArgumentPart argument) {
                Optional<Intrinsic> measure = measureIntrinsic(argument.type());
                if (measure.isPresent()) {
                    Value next = context.values().int32();
                    context.emit(new IrInstruction.IntrinsicCall(Optional.of(next), measure.get(),
                            Optional.of(length), List.of(arguments.get(argument.index())), List.of()));
                    length = next;
                }
            }
        }
        return length;
    }

    private static Optional<Intrinsic> measureIntrinsic(String type) {
        return switch (type) {
            case STRING -> Optional.of(Intrinsic.STRING_CONCAT_MEASURE_STRING);
            case "B", "S", "I" -> Optional.of(Intrinsic.STRING_CONCAT_MEASURE_INT);
            case "J" -> Optional.of(Intrinsic.STRING_CONCAT_MEASURE_LONG);
            case "F" -> Optional.of(Intrinsic.STRING_CONCAT_MEASURE_FLOAT);
            case "D" -> Optional.of(Intrinsic.STRING_CONCAT_MEASURE_DOUBLE);
            default -> Optional.empty();
        };
    }

    /** A literal's length as the board stores it, in UTF-8 bytes. */
    private static int literalLength(StringConcatSite.LiteralPart literal) {
        return literal.value().getBytes(StandardCharsets.UTF_8).length;
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
