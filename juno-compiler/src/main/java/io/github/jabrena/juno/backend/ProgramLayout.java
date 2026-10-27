package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.ConstantTables;
import io.github.jabrena.juno.linker.LambdaSite;
import io.github.jabrena.juno.linker.ThrowableTypes;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Whole-program storage decisions made before any code is emitted: arena object sizes and field
 * offsets, mutable static fields' {@code .bss} symbols, and the {@code .rodata} symbols of string
 * literals and compiler-generated constant {@code int} arrays — plus the data sections holding them.
 */
public final class ProgramLayout {
    private static final int TABLE_VALUES_PER_LINE = 16;
    private final Map<String, Integer> objectSizes = new LinkedHashMap<>();
    private final Map<FieldRef, Integer> fieldOffsets = new LinkedHashMap<>();
    private final Map<FieldRef, String> staticSymbols = new LinkedHashMap<>();
    private final Map<String, String> stringLiteralSymbols = new LinkedHashMap<>();
    private final Map<IrInstruction.IntArrayConst, String> intArraySymbols = new LinkedHashMap<>();
    private final Map<FieldRef, ConstantTables.Table> constantTables = new TreeMap<>(
            Comparator.comparing(FieldRef::displayName));
    private final Map<LambdaSite, String> lambdaFunctionSymbols = new LinkedHashMap<>();
    private final List<String> throwableClasses;
    private final Map<String, Integer> objectTypeIds;
    private final Map<String, String> superClasses;

    /**
     * An object's fields are exactly the ones some {@code LoadField}/{@code StoreField} in the whole
     * program actually touches (there's no
     * class-file field table available here, only the IR), laid out in {@code FieldRef.displayName()}
     * order — sequential 4-byte {@code int} slots, since every field this backend has seen is one.
     * Also assigns each {@code LoadStatic}/{@code StoreStatic} field a stable {@code .bss} symbol.
     */
    public ProgramLayout(IrProgram program) {
        throwableClasses = program.throwableClasses();
        objectTypeIds = program.objectTypeIds();
        superClasses = program.superClasses();
        Map<String, SortedMap<String, FieldRef>> layouts = new TreeMap<>();
        for (IrMethod method : program.methods()) {
            for (IrBasicBlock block : method.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    collect(layouts, instruction);
                }
            }
        }
        for (Map.Entry<String, SortedMap<String, FieldRef>> layout : layouts.entrySet()) {
            int offset = instancePrefix(layout.getKey(), layouts);
            for (FieldRef field : layout.getValue().values()) {
                fieldOffsets.put(field, offset);
                offset += fieldWidth(field);
            }
            objectSizes.put(layout.getKey(), Math.max(offset, FrameLayout.WORD));
        }
    }

    private int instancePrefix(String className, Map<String, SortedMap<String, FieldRef>> layouts) {
        String parent = superClasses.get(className);
        int inherited = parent == null ? 0 : instanceSize(parent, layouts);
        return Math.max(inherited, headerBytes(className));
    }

    private int instanceSize(String className, Map<String, SortedMap<String, FieldRef>> layouts) {
        SortedMap<String, FieldRef> fields = layouts.get(className);
        int size = instancePrefix(className, layouts);
        if (fields != null) {
            for (FieldRef field : fields.values()) {
                size += fieldWidth(field);
            }
        }
        return size;
    }

    private int headerBytes(String className) {
        return throwableClasses.contains(className) ? ThrowableTypes.HEADER_BYTES
                : objectTypeIds.containsKey(className) ? FrameLayout.WORD : 0;
    }

    /** Bytes a field occupies: a {@code long}/{@code double} takes two words, everything else one. */
    private int fieldWidth(FieldRef field) {
        return field.descriptor().equals("J") || field.descriptor().equals("D") ? 2 * FrameLayout.WORD : FrameLayout.WORD;
    }

    private void collect(Map<String, SortedMap<String, FieldRef>> layouts, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.NewObject object ->
                    layouts.computeIfAbsent(object.className(), _ -> new TreeMap<>());
            case IrInstruction.LambdaCreate lambda -> {
                if (lambda.site().isCapturing()) {
                    objectSizes.put(lambda.site().syntheticClassName(),
                            (lambda.captures().size() + 1) * FrameLayout.WORD);
                } else {
                    lambdaFunctionSymbols.computeIfAbsent(lambda.site(),
                            _ -> "juno_lambda_" + lambdaFunctionSymbols.size());
                }
            }
            case IrInstruction.LambdaCall call -> {
                if (call.site().implementation().referenceKind() == MethodHandleRef.REF_NEW_INVOKE_SPECIAL) {
                    layouts.computeIfAbsent(call.site().implementation().method().owner(), _ -> new TreeMap<>());
                }
            }
            case IrInstruction.LoadField load -> layouts
                    .computeIfAbsent(load.field().owner(), _ -> new TreeMap<>())
                    .put(load.field().displayName(), load.field());
            case IrInstruction.StoreField store -> layouts
                    .computeIfAbsent(store.field().owner(), _ -> new TreeMap<>())
                    .put(store.field().displayName(), store.field());
            case IrInstruction.LoadStatic load -> staticSymbols.computeIfAbsent(
                    load.field(), field -> "juno_static_" + sanitize(field.displayName()));
            case IrInstruction.StoreStatic store -> staticSymbols.computeIfAbsent(
                    store.field(), field -> "juno_static_" + sanitize(field.displayName()));
            case IrInstruction.IntArrayConst array -> intArraySymbols.computeIfAbsent(
                    array, _ -> "juno_int_array" + intArraySymbols.size());
            case IrInstruction.ConstantTableRef table -> constantTables.put(table.table().field(), table.table());
            case IrInstruction.StringConst constant -> stringLiteralSymbols.computeIfAbsent(
                    constant.value(), _ -> "juno_str" + stringLiteralSymbols.size());
            case IrInstruction.IntrinsicCall call -> {
                for (String literal : call.literalArguments()) {
                    stringLiteralSymbols.computeIfAbsent(literal,
                            _ -> "juno_str" + stringLiteralSymbols.size());
                }
            }
            default -> { }
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /** Every throwable class, in class-id order (see {@link ThrowableTypes}). */
    public List<String> throwableClasses() {
        return throwableClasses;
    }

    public @Nullable Integer objectTypeId(String className) {
        return objectTypeIds.get(className);
    }

    public Map<String, String> stringLiteralSymbols() {
        return stringLiteralSymbols;
    }

    public int objectSize(String className) {
        return Objects.requireNonNull(objectSizes.get(className), () -> "No object size for " + className);
    }

    public int fieldOffset(FieldRef field) {
        return Objects.requireNonNull(fieldOffsets.get(field), () -> "No field offset for " + field.displayName());
    }

    public String staticSymbol(FieldRef field) {
        return Objects.requireNonNull(staticSymbols.get(field), () -> "No static symbol for " + field.displayName());
    }

    public String intArraySymbol(IrInstruction.IntArrayConst array) {
        return Objects.requireNonNull(intArraySymbols.get(array), "No symbol for an int array constant");
    }

    public String constantTableSymbol(FieldRef field) {
        return "juno_table_" + sanitize(field.displayName());
    }

    public String lambdaFunctionSymbol(LambdaSite site) {
        return Objects.requireNonNull(lambdaFunctionSymbols.get(site), () -> "No function symbol for " + site);
    }

    /**
     * The assembler directives a data section is written with: {@code word} emits 32-bit values (ARM's
     * {@code .word}; AVR's {@code .word} is 16-bit, so it uses {@code .long}), {@code align} 4-byte-aligns, and
     * {@code bss} switches to the zero-initialized section (AVR's assembler has no {@code .bss} shorthand).
     */
    public record DataDirectives(String word, String align, String bss, String lengthWord) {
        public static final DataDirectives THUMB2 = new DataDirectives(".word", ".align 2", ".bss", ".word");
        public static final DataDirectives AVR = new DataDirectives(".long", ".balign 4", ".section .bss", ".short");
        public static final DataDirectives XTENSA = new DataDirectives(".long", ".balign 4", ".section .bss", ".long");
    }

    private DataDirectives directives = DataDirectives.THUMB2;

    /** Emits every data section the program needs, ahead of its {@code .text}. */
    public void emitDataSections(StringBuilder output) {
        emitDataSections(output, DataDirectives.THUMB2);
    }

    /** Emits every data section with {@code directives}, ahead of the {@code .text}. */
    public void emitDataSections(StringBuilder output, DataDirectives directives) {
        this.directives = directives;
        emitStaticStorage(output);
        emitGcStackTopStorage(output);
        emitStringLiteralStorage(output);
        emitIntArrayStorage(output);
        emitConstantTableStorage(output);
        emitLambdaFunctionStorage(output);
    }

    private void emitStaticStorage(StringBuilder output) {
        output.append("    ").append(directives.bss()).append('\n')
                .append("    ").append(directives.align()).append('\n')
                .append("""
                            .global juno_gc_static_start
                            .global juno_gc_static_end
                        juno_gc_static_start:
                        """);
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
        output.append("    ").append(directives.bss()).append('\n')
                .append("    ").append(directives.align()).append('\n')
                // Unlike juno_static_* fields (only ever referenced within this same .S file), the
                // runtime shim's C++ juno_gc_mark() references this symbol too — .global is required
                // for that cross-object-file link to resolve; without it, `as` gives it local linkage
                // and only a program that actually calls juno_alloc surfaces the failure (a program
                // that never allocates has this whole path stripped by the linker's --gc-sections
                // before the missing symbol would matter).
                .append("""
                            .global juno_gc_stack_top
                        juno_gc_stack_top:
                            .space 4
                        """);
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
                .append("    ").append(directives.align()).append('\n');
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
                .append("    ").append(directives.align()).append('\n');
        for (Map.Entry<IrInstruction.IntArrayConst, String> entry : intArraySymbols.entrySet()) {
            List<Integer> values = entry.getKey().values();
            output.append("    ").append(directives.lengthWord()).append(' ').append(values.size()).append('\n')
                    .append(entry.getValue()).append(":\n")
                    .append("    ").append(directives.word()).append(' ');
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    output.append(", ");
                }
                output.append(values.get(index));
            }
            output.append('\n');
        }
    }

    /**
     * Read-only {@code static final} lookup tables, in flash rather than the arena, each element at the width
     * the backend's array loads expect (wide elements as two little-endian words), in field-name order.
     */
    private void emitConstantTableStorage(StringBuilder output) {
        if (constantTables.isEmpty()) {
            return;
        }
        output.append("    .section .rodata\n")
                .append("    ").append(directives.align()).append('\n');
        for (ConstantTables.Table table : constantTables.values()) {
            output.append("    ").append(directives.lengthWord()).append(' ').append(table.length()).append('\n')
                    .append(constantTableSymbol(table.field())).append(":\n");
            String directive = switch (table.elementDescriptor()) {
                case 'Z', 'B' -> "    .byte ";
                case 'C', 'S' -> "    .short ";
                default -> "    " + directives.word() + " ";
            };
            boolean wide = table.elementDescriptor() == 'J' || table.elementDescriptor() == 'D';
            List<Long> values = table.values();
            for (int line = 0; line < values.size(); line += TABLE_VALUES_PER_LINE) {
                output.append(directive);
                for (int index = line; index < Math.min(values.size(), line + TABLE_VALUES_PER_LINE); index++) {
                    if (index > line) {
                        output.append(", ");
                    }
                    long value = values.get(index);
                    if (wide) {
                        output.append((int) value).append(", ").append((int) (value >>> 32));
                    } else {
                        output.append((int) value);
                    }
                }
                output.append('\n');
            }
            output.append("    ").append(directives.align()).append('\n');
        }
    }

    /** Non-capturing lambdas are immutable one-word function references, never arena allocations. */
    private void emitLambdaFunctionStorage(StringBuilder output) {
        if (lambdaFunctionSymbols.isEmpty()) {
            return;
        }
        output.append("    .section .rodata\n")
                .append("    ").append(directives.align()).append('\n');
        for (Map.Entry<LambdaSite, String> entry : lambdaFunctionSymbols.entrySet()) {
            output.append(entry.getValue()).append(":\n")
                    .append("    ").append(directives.word()).append(' ')
                    .append(objectTypeIds.getOrDefault(entry.getKey().syntheticClassName(), 0))
                    .append('\n');
        }
    }

    /** Escapes {@code value} for a GNU {@code as} {@code .asciz} directive. */
    private String asmStringLiteral(String value) {
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
