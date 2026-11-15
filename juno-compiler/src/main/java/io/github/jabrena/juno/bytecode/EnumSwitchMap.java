package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.github.jabrena.juno.bytecode.Opcodes.GETSTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.IASTORE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEVIRTUAL;

/**
 * Reads the synthetic {@code $SwitchMap$} array javac builds for a {@code switch} over an enum. The array maps each
 * constant's ordinal to the key the switch actually branches on, and its only writer is the synthetic class's
 * {@code <clinit>}, one {@code map[Constant.ordinal()] = <literal>} at a time, so the whole table is known at compile
 * time.
 *
 * <p>Both the linker and the lowering need it: the linker to decide a switch over a board model before reachability,
 * the lowering to turn the array into a constant table.
 */
public final class EnumSwitchMap {
    private static final String SWITCH_MAP_PREFIX = "$SwitchMap$";

    private final Map<String, JavaClass> classes;
    private final BytecodeDecoder decoder;

    public EnumSwitchMap(Map<String, JavaClass> classes, BytecodeDecoder decoder) {
        this.classes = classes;
        this.decoder = decoder;
    }

    /** Whether {@code field} names one of javac's synthetic switch-map arrays. */
    public static boolean isSwitchMap(FieldRef field) {
        return field.name().startsWith(SWITCH_MAP_PREFIX);
    }

    /**
     * The per-ordinal keys of {@code requested}, or {@code null} when the table cannot be read: a caller that must
     * have it reports the failure itself, and a caller that was only hoping to fold a branch leaves the branch alone.
     */
    public @Nullable List<Integer> resolve(FieldRef requested) {
        JavaClass mappingClass = classes.get(requested.owner());
        JavaMethod initializer = mappingClass == null ? null : mappingClass.findMethod("<clinit>", "()V");
        if (mappingClass == null || initializer == null) {
            return null;
        }
        IntLiterals literals = new IntLiterals(mappingClass.constantPool());
        List<Instruction> bytecode = decoder.decode(initializer);
        List<Integer> mapping = null;
        for (int index = 4; index < bytecode.size(); index++) {
            if (bytecode.get(index).opcode() != IASTORE) {
                continue;
            }
            Instruction mapLoad = bytecode.get(index - 4);
            Instruction enumLoad = bytecode.get(index - 3);
            Instruction ordinalCall = bytecode.get(index - 2);
            if (mapLoad.opcode() != GETSTATIC || enumLoad.opcode() != GETSTATIC
                    || ordinalCall.opcode() != INVOKEVIRTUAL
                    || !mappingClass.constantPool().fieldRef(mapLoad.operandA()).equals(requested)) {
                continue;
            }
            FieldRef enumField = mappingClass.constantPool().fieldRef(enumLoad.operandA());
            JavaClass enumClass = classes.get(enumField.owner());
            int ordinal = enumClass == null ? -1 : enumClass.enumConstantNames().indexOf(enumField.name());
            Integer switchValue = literals.pushedBy(bytecode.get(index - 1));
            if (enumClass == null || ordinal < 0 || switchValue == null) {
                return null;
            }
            if (mapping == null) {
                mapping = new ArrayList<>(Collections.nCopies(enumClass.enumConstantNames().size(), 0));
            }
            mapping.set(ordinal, switchValue);
        }
        return mapping == null ? null : List.copyOf(mapping);
    }

    /** The ordinal of the enum constant {@code field} names, or {@code -1} when it is not a constant of a loaded enum. */
    public int ordinalOf(FieldRef field) {
        JavaClass enumClass = classes.get(field.owner());
        return enumClass == null ? -1 : enumClass.enumConstantNames().indexOf(field.name());
    }
}
