package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves a synthetic enum {@code switch} lookup table's per-ordinal values, split out of {@link BytecodeToIr}. */
final class EnumSwitchSupport {
    private EnumSwitchSupport() {
    }
    static List<Integer> resolveEnumSwitchMap(FieldRef requested, Map<String, JavaClass> classes,
                                              BytecodeDecoder decoder) {
        JavaClass mappingClass = classes.get(requested.owner());
        JavaMethod initializer = mappingClass == null ? null : mappingClass.findMethod("<clinit>", "()V");
        if (mappingClass == null || initializer == null) {
            throw new CompileException("Synthetic enum switch map has no initializer: " + requested.displayName());
        }
        List<Instruction> bytecode = decoder.decode(initializer);
        List<Integer> mapping = null;
        for (int index = 4; index < bytecode.size(); index++) {
            if (bytecode.get(index).opcode() != 79) {
                continue;
            }
            Instruction mapLoad = bytecode.get(index - 4);
            Instruction enumLoad = bytecode.get(index - 3);
            Instruction ordinalCall = bytecode.get(index - 2);
            Instruction valuePush = bytecode.get(index - 1);
            if (mapLoad.opcode() != 178 || enumLoad.opcode() != 178 || ordinalCall.opcode() != 182) {
                continue;
            }
            FieldRef mapField = mappingClass.constantPool().fieldRef(mapLoad.operandA());
            if (!mapField.equals(requested)) {
                continue;
            }
            FieldRef enumField = mappingClass.constantPool().fieldRef(enumLoad.operandA());
            JavaClass enumClass = classes.get(enumField.owner());
            int ordinal = enumClass == null ? -1 : enumClass.enumConstantNames().indexOf(enumField.name());
            Integer switchValue = literalValue(valuePush, mappingClass);
            if (enumClass == null || ordinal < 0 || switchValue == null) {
                throw new CompileException("Cannot resolve synthetic enum switch entry for " + requested.displayName());
            }
            if (mapping == null) {
                mapping = new ArrayList<>();
                for (int item = 0; item < enumClass.enumConstantNames().size(); item++) {
                    mapping.add(0);
                }
            }
            mapping.set(ordinal, switchValue);
        }
        if (mapping == null) {
            throw new CompileException("Cannot resolve synthetic enum switch map: " + requested.displayName());
        }
        return List.copyOf(mapping);
    }

    static @Nullable Integer literalValue(Instruction instruction, JavaClass owner) {
        return switch (instruction.opcode()) {
            case 2 -> -1;
            case 3, 4, 5, 6, 7, 8 -> instruction.opcode() - 3;
            case 16, 17 -> instruction.operandA();
            case 18, 19 -> owner.constantPool().integer(instruction.operandA());
            default -> null;
        };
    }
}
