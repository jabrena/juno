package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;

import java.util.Map;

import static io.github.jabrena.juno.bytecode.Opcodes.PUTSTATIC;

/**
 * Rejects reading an enum's static field that only the enum's {@code <clinit>} assigns. Juno never runs an enum's
 * static initializer, since its constants are ordinals rather than objects, so such a field would read as zero on the
 * board, a {@code null} array or object, instead of the value the source gives it. A static field the enum's methods
 * assign needs no initializer and stays supported.
 */
final class EnumStaticFieldGuard {
    private final Map<String, JavaClass> classes;
    private final BytecodeDecoder decoder;

    EnumStaticFieldGuard(Map<String, JavaClass> classes, BytecodeDecoder decoder) {
        this.classes = classes;
        this.decoder = decoder;
    }

    /** Throws when {@code field} belongs to an enum whose static initializer assigns it. */
    void requireNoInitializerOnlyStatic(FieldRef field) {
        JavaClass owner = classes.get(field.owner());
        JavaMethod initializer = owner == null || !owner.isEnum() ? null : owner.findMethod("<clinit>", "()V");
        if (owner == null || initializer == null) {
            return;
        }
        for (Instruction instruction : decoder.decode(initializer)) {
            if (instruction.opcode() == PUTSTATIC
                    && owner.constantPool().fieldRef(instruction.operandA()).equals(field)) {
                throw new CompileException("Enum static fields initialized in the enum are not supported: "
                        + field.displayName() + " (Juno does not run an enum's static initializer; use a switch over"
                        + " the constants or move the field to a class)");
            }
        }
    }
}
