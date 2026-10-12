package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.AtomicSupport;
import io.github.jabrena.juno.linker.BigNumberSupport;
import io.github.jabrena.juno.linker.ConstantTables;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LockSupport;
import io.github.jabrena.juno.linker.ScopedValueSupport;
import io.github.jabrena.juno.linker.ThreadSupport;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.bytecode.Opcodes.GETFIELD;
import static io.github.jabrena.juno.bytecode.Opcodes.GETSTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.PUTFIELD;
import static io.github.jabrena.juno.bytecode.Opcodes.PUTSTATIC;

/**
 * {@code getstatic}/{@code putstatic}/{@code getfield}/{@code putfield}. A static read may resolve at compile time
 * instead: to a flash table, a {@code BigInteger}/{@code BigDecimal} constant, an enum ordinal or an enum switch map.
 */
final class FieldLowering implements InstructionLowering {
    private final ThreadSupport threadSupport = new ThreadSupport();

    private final AtomicSupport atomicSupport = new AtomicSupport();

    private final LockSupport lockSupport = new LockSupport();

    private final ScopedValueSupport scopedValueSupport = new ScopedValueSupport();

    private final BigNumberSupport bigNumberSupport = new BigNumberSupport();

    private final MethodContext context;
    private final OperandStack stack;
    private final FreshValues values;
    private final EnumResolver enums;

    FieldLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
        this.values = context.values();
        this.enums = new EnumResolver(context.classes(), context.program().decoder());
    }

    @Override
    public void lower(Instruction instruction) {
        FieldRef field = context.constantPool().fieldRef(instruction.operandA());
        switch (instruction.opcode()) {
            case GETSTATIC -> getStatic(instruction, field);
            case PUTSTATIC -> {
                Value value = stack.pop(staticFieldType(instruction, field));
                context.emit(new IrInstruction.StoreStatic(field, value));
            }
            case GETFIELD -> getField(instruction, field);
            case PUTFIELD -> {
                Value value = stack.pop(instanceFieldType(instruction, field));
                Value receiver = stack.pop();
                context.emit(new IrInstruction.StoreField(field, receiver, value));
            }
            default -> throw new IllegalStateException("Not a field opcode: " + instruction.opcode());
        }
    }

    private void getStatic(Instruction instruction, FieldRef field) {
        Integer ordinal = enums.ordinal(field);
        Optional<BigNumberSupport.Constant> bigConstant = bigNumberSupport.constant(field);
        ConstantTables.Table table = context.program().constantTables().table(field);
        if (table != null) {
            Value target = values.int32();
            context.emit(new IrInstruction.ConstantTableRef(target, table));
            context.facts().markKnownArray(target, table.length());
            stack.push(target);
        } else if (bigConstant.isPresent()) {
            pushBigConstant(bigConstant.get());
        } else if (ordinal != null) {
            stack.pushInt(ordinal);
        } else if (field.name().startsWith("$SwitchMap$")) {
            List<Integer> mapping = enums.switchMap(field);
            Value target = values.int32();
            context.emit(new IrInstruction.IntArrayConst(target, mapping));
            context.facts().markKnownArray(target, mapping.size());
            stack.push(target);
        } else {
            Value target = values.of(staticFieldType(instruction, field));
            context.emit(new IrInstruction.LoadStatic(target, field));
            stack.push(target);
        }
    }

    private void pushBigConstant(BigNumberSupport.Constant constant) {
        if (constant.intrinsic().isEmpty()) {
            stack.pushInt(constant.value());
            return;
        }
        Value which = values.int32();
        context.emit(new IrInstruction.Const(which, constant.value()));
        Value handle = values.int32();
        context.emit(new IrInstruction.IntrinsicCall(Optional.of(handle), constant.intrinsic().get(),
                Optional.empty(), List.of(which), List.of()));
        stack.push(handle);
    }

    /** An enum's integer field reads from a per-ordinal table, since the "object" is just its ordinal. */
    private void getField(Instruction instruction, FieldRef field) {
        JunoType type = instanceFieldType(instruction, field);
        Value receiver = stack.pop();
        Value target = values.of(type);
        List<Integer> enumFieldValues = enums.integerFieldValues(field);
        if (enumFieldValues != null) {
            Value table = values.int32();
            context.emit(new IrInstruction.IntArrayConst(table, enumFieldValues));
            context.emit(new IrInstruction.BoundsCheck(receiver, enumFieldValues.size()));
            context.emit(new IrInstruction.ArrayLoad(target, ArrayElementType.INT, table, receiver));
        } else {
            context.emit(new IrInstruction.LoadField(target, field, receiver));
        }
        stack.push(target);
    }

    private JunoType staticFieldType(Instruction instruction, FieldRef field) {
        FieldInfo declaration = declaration(field);
        if (declaration == null || !declaration.isStatic()) {
            throw new CompileException(context.at(instruction) + ": static field not found: " + field.displayName());
        }
        return typeOf(instruction, field);
    }

    private JunoType instanceFieldType(Instruction instruction, FieldRef field) {
        FieldInfo declaration = declaration(field);
        if (declaration == null || declaration.isStatic()) {
            throw new CompileException(context.at(instruction) + ": instance field not found: "
                    + field.displayName());
        }
        return typeOf(instruction, field);
    }

    private @Nullable FieldInfo declaration(FieldRef field) {
        JavaClass owner = context.classes().get(field.owner());
        return owner == null ? null : owner.findField(field.name(), field.descriptor());
    }

    /** Every reference Juno supports (strings, arrays, handles, program objects) is a 32-bit word. */
    private JunoType typeOf(Instruction instruction, FieldRef field) {
        String descriptor = field.descriptor();
        if (Descriptor.isIntegerLike(descriptor)) {
            return JunoType.INT32;
        }
        if (Descriptor.isLong(descriptor)) {
            return JunoType.INT64;
        }
        if (Descriptor.isFloat(descriptor)) {
            return JunoType.FLOAT32;
        }
        if (Descriptor.isDouble(descriptor)) {
            return JunoType.FLOAT64;
        }
        if (Descriptor.isString(descriptor)
                || threadSupport.isThreadType(descriptor)
                || scopedValueSupport.isScopedValueType(descriptor)
                || bigNumberSupport.isBigNumberType(descriptor)
                || lockSupport.isReentrantLockType(descriptor)
                || atomicSupport.isAtomicType(descriptor)
                || Descriptor.isArrayType(descriptor)
                || Descriptor.isReferenceType(descriptor, context.classes().keySet())) {
            return JunoType.INT32;
        }
        throw new CompileException(context.at(instruction) + ": unsupported field type: " + field.displayName());
    }
}
