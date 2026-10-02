package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.ThrowableTypes;
import io.github.jabrena.juno.linker.LambdaSite;
import io.github.jabrena.juno.classfile.MethodHandleRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Whole-program storage decisions made before any code is emitted: arena object sizes and field
 * offsets, mutable static fields' {@code .bss} symbols, and the {@code .rodata} symbols of string
 * literals and compiler-generated constant {@code int} arrays — plus the data sections holding them.
 */
final class ProgramLayout {
    private final Map<String, Integer> objectSizes = new LinkedHashMap<>();
    private final Map<FieldRef, Integer> fieldOffsets = new LinkedHashMap<>();
    private final Map<FieldRef, String> staticSymbols = new LinkedHashMap<>();
    private final Map<String, String> stringLiteralSymbols = new LinkedHashMap<>();
    private final Map<IrInstruction.IntArrayConst, String> intArraySymbols = new LinkedHashMap<>();
    private final Map<LambdaSite, String> lambdaFunctionSymbols = new LinkedHashMap<>();
    private final List<String> throwableClasses;
    private final Map<String, Integer> objectTypeIds;

    /**
     * An object's fields are exactly the ones some {@code LoadField}/{@code StoreField} in the whole
     * program actually touches (there's no
     * class-file field table available here, only the IR), laid out in {@code FieldRef.displayName()}
     * order — sequential 4-byte {@code int} slots, since every field this backend has seen is one.
     * Also assigns each {@code LoadStatic}/{@code StoreStatic} field a stable {@code .bss} symbol.
     */
    ProgramLayout(IrProgram program) {
        throwableClasses = program.throwableClasses();
        objectTypeIds = program.objectTypeIds();
        Map<String, TreeMap<String, FieldRef>> layouts = new TreeMap<>();
        for (IrMethod method : program.methods()) {
            for (IrBasicBlock block : method.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    collect(layouts, instruction);
                }
            }
        }
        for (Map.Entry<String, TreeMap<String, FieldRef>> layout : layouts.entrySet()) {
            // A throwable's own fields follow its class-id/message header (see ThrowableTypes).
            int header = throwableClasses.contains(layout.getKey())
                    ? ThrowableTypes.HEADER_BYTES
                    : objectTypeIds.containsKey(layout.getKey()) ? AsmEmitter.WORD : 0;
            int offset = header;
            for (FieldRef field : layout.getValue().values()) {
                fieldOffsets.put(field, offset);
                offset += fieldWidth(field);
            }
            objectSizes.put(layout.getKey(), Math.max(offset, AsmEmitter.WORD));
        }
    }

    /** Bytes a field occupies: a {@code long}/{@code double} takes two words, everything else one. */
    static int fieldWidth(FieldRef field) {
        return field.descriptor().equals("J") || field.descriptor().equals("D") ? 2 * AsmEmitter.WORD : AsmEmitter.WORD;
    }

    private void collect(Map<String, TreeMap<String, FieldRef>> layouts, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.NewObject object ->
                    layouts.computeIfAbsent(object.className(), unused -> new TreeMap<>());
            case IrInstruction.LambdaCreate lambda -> {
                if (lambda.site().isCapturing()) {
                    objectSizes.put(lambda.site().syntheticClassName(),
                            (lambda.captures().size() + 1) * AsmEmitter.WORD);
                } else {
                    lambdaFunctionSymbols.computeIfAbsent(lambda.site(),
                            unused -> "juno_lambda_" + lambdaFunctionSymbols.size());
                }
            }
            case IrInstruction.LambdaCall call -> {
                if (call.site().implementation().referenceKind() == MethodHandleRef.REF_NEW_INVOKE_SPECIAL) {
                    layouts.computeIfAbsent(call.site().implementation().method().owner(), unused -> new TreeMap<>());
                }
            }
            case IrInstruction.LoadField load -> layouts
                    .computeIfAbsent(load.field().owner(), unused -> new TreeMap<>())
                    .put(load.field().displayName(), load.field());
            case IrInstruction.StoreField store -> layouts
                    .computeIfAbsent(store.field().owner(), unused -> new TreeMap<>())
                    .put(store.field().displayName(), store.field());
            case IrInstruction.LoadStatic load -> staticSymbols.computeIfAbsent(
                    load.field(), field -> "juno_static_" + sanitize(field.displayName()));
            case IrInstruction.StoreStatic store -> staticSymbols.computeIfAbsent(
                    store.field(), field -> "juno_static_" + sanitize(field.displayName()));
            case IrInstruction.IntArrayConst array -> intArraySymbols.computeIfAbsent(
                    array, unused -> "juno_int_array" + intArraySymbols.size());
            case IrInstruction.StringConst constant -> stringLiteralSymbols.computeIfAbsent(
                    constant.value(), unused -> "juno_str" + stringLiteralSymbols.size());
            case IrInstruction.IntrinsicCall call -> {
                for (String literal : call.literalArguments()) {
                    stringLiteralSymbols.computeIfAbsent(literal,
                            unused -> "juno_str" + stringLiteralSymbols.size());
                }
            }
            default -> { }
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** Every throwable class, in class-id order (see {@link ThrowableTypes}). */
    List<String> throwableClasses() {
        return throwableClasses;
    }

    Integer objectTypeId(String className) {
        return objectTypeIds.get(className);
    }

    Map<String, String> stringLiteralSymbols() {
        return stringLiteralSymbols;
    }

    int objectSize(String className) {
        return objectSizes.get(className);
    }

    int fieldOffset(FieldRef field) {
        return fieldOffsets.get(field);
    }

    String staticSymbol(FieldRef field) {
        return staticSymbols.get(field);
    }

    String intArraySymbol(IrInstruction.IntArrayConst array) {
        return intArraySymbols.get(array);
    }

    String lambdaFunctionSymbol(LambdaSite site) {
        return lambdaFunctionSymbols.get(site);
    }

    /** Emits every data section the program needs, ahead of its {@code .text}. */
    void emitDataSections(StringBuilder output) {
        emitStaticStorage(output);
        emitGcStackTopStorage(output);
        emitStringLiteralStorage(output);
        emitIntArrayStorage(output);
        emitLambdaFunctionStorage(output);
    }

    private void emitStaticStorage(StringBuilder output) {
        output.append("    .bss\n")
                .append("    .align 2\n")
                .append("    .global juno_gc_static_start\n")
                .append("    .global juno_gc_static_end\n")
                .append("juno_gc_static_start:\n");
        for (Map.Entry<FieldRef, String> entry : staticSymbols.entrySet()) {
            output.append(entry.getValue()).append(":\n")
                    .append("    .space ").append(fieldWidth(entry.getKey())).append('\n');
        }
        output.append("juno_gc_static_end:\n");
    }

    /**
     * The conservative GC's stack scan needs this bound regardless of whether the program happens
     * to use static fields.
     */
    private void emitGcStackTopStorage(StringBuilder output) {
        output.append("    .bss\n")
                .append("    .align 2\n")
                // Unlike juno_static_* fields (only ever referenced within this same .S file), the
                // runtime shim's C++ juno_gc_mark() references this symbol too — .global is required
                // for that cross-object-file link to resolve; without it, `as` gives it local linkage
                // and only a program that actually calls juno_alloc surfaces the failure (a program
                // that never allocates has this whole path stripped by the linker's --gc-sections
                // before the missing symbol would matter).
                .append("    .global juno_gc_stack_top\n")
                .append("juno_gc_stack_top:\n")
                .append("    .space 4\n");
    }

    /**
     * Every distinct string literal used anywhere in the program (deduplicated by content) gets one
     * {@code .rodata} symbol, filled in by the constructor; a call site just loads its address.
     */
    private void emitStringLiteralStorage(StringBuilder output) {
        if (stringLiteralSymbols.isEmpty()) {
            return;
        }
        output.append("    .section .rodata\n")
                .append("    .align 2\n");
        for (Map.Entry<String, String> entry : stringLiteralSymbols.entrySet()) {
            output.append(entry.getValue()).append(":\n")
                    .append("    .asciz \"").append(asmStringLiteral(entry.getKey())).append("\"\n");
        }
    }

    /** Emits compiler-created immutable integer arrays used by enum values and switch maps. */
    private void emitIntArrayStorage(StringBuilder output) {
        if (intArraySymbols.isEmpty()) {
            return;
        }
        output.append("    .section .rodata\n")
                .append("    .align 2\n");
        for (Map.Entry<IrInstruction.IntArrayConst, String> entry : intArraySymbols.entrySet()) {
            output.append(entry.getValue()).append(":\n")
                    .append("    .word ");
            List<Integer> values = entry.getKey().values();
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }
                output.append(values.get(index));
            }
            output.append('\n');
        }
    }

    /** Non-capturing lambdas are immutable one-word function references, never arena allocations. */
    private void emitLambdaFunctionStorage(StringBuilder output) {
        if (lambdaFunctionSymbols.isEmpty()) {
            return;
        }
        output.append("    .section .rodata\n")
                .append("    .align 2\n");
        for (Map.Entry<LambdaSite, String> entry : lambdaFunctionSymbols.entrySet()) {
            output.append(entry.getValue()).append(":\n")
                    .append("    .word ")
                    .append(objectTypeIds.getOrDefault(entry.getKey().syntheticClassName(), 0))
                    .append('\n');
        }
    }

    /** Escapes {@code value} for a GNU {@code as} {@code .asciz} directive. */
    private static String asmStringLiteral(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20 || character > 0x7E) {
                        escaped.append(String.format("\\%03o", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
