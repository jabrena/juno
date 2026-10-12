package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.ir.ArrayDeclaration;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.List;

import static io.github.jabrena.juno.bytecode.Opcodes.*;

/**
 * Array element loads and stores, array creation and {@code arraylength}. There is no heap for arrays of unknown
 * size: every array is created with a compile-time-constant length, so an access to an array whose length is known
 * here gets a bounds check, and {@code arraylength} resolves at compile time.
 */
final class ArrayLowering implements OpcodeGroup {
    private final MethodContext context;
    private final OperandStack stack;
    private final FreshValues values;

    ArrayLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
        this.values = context.values();
    }

    @Override
    public void registerOn(OpcodeTable table) {
        List<ArrayElementType> elementTypes = List.of(ArrayElementType.INT, ArrayElementType.LONG,
                ArrayElementType.FLOAT, ArrayElementType.DOUBLE, ArrayElementType.REFERENCE, ArrayElementType.BYTE,
                ArrayElementType.CHAR, ArrayElementType.SHORT);
        // iaload..saload and iastore..sastore list the element types in the same order.
        for (int index = 0; index < elementTypes.size(); index++) {
            ArrayElementType elementType = elementTypes.get(index);
            table.on(IALOAD + index, instruction -> load(elementType));
            table.on(IASTORE + index, instruction -> store(elementType));
        }
        table.on(NEWARRAY, instruction -> newArray(instruction, ArrayElementType.fromAtype(instruction.operandA())
                .orElseThrow(() -> new CompileException(context.at(instruction)
                        + ": unsupported primitive array atype " + instruction.operandA()))));
        table.on(ANEWARRAY, instruction -> newArray(instruction, ArrayElementType.REFERENCE));
        table.on(MULTIANEWARRAY, this::newMultiArray);
        table.on(ARRAYLENGTH, this::arrayLength);
    }

    private void arrayLength(Instruction instruction) {
        Value array = stack.pop();
        Integer knownLength = context.facts().knownLength(array);
        if (knownLength == null) {
            throw new CompileException(context.at(instruction) + ": array length is not known at compile time here "
                    + "(only supported on a local array created once in this method with a compile-time-constant "
                    + "size)");
        }
        stack.pushInt(knownLength);
    }

    private void load(ArrayElementType elementType) {
        Value index = stack.pop();
        Value array = stack.pop();
        checkBounds(array, index);
        Value element = values.of(elementType.valueType());
        context.emit(new IrInstruction.ArrayLoad(element, elementType, array, index));
        stack.push(element);
    }

    private void store(ArrayElementType elementType) {
        Value element = stack.pop(elementType.valueType());
        Value index = stack.pop();
        Value array = stack.pop();
        checkBounds(array, index);
        context.emit(new IrInstruction.ArrayStore(elementType, array, index, element));
    }

    private void checkBounds(Value array, Value index) {
        Integer knownLength = context.facts().knownLength(array);
        if (knownLength != null) {
            context.emit(new IrInstruction.BoundsCheck(index, knownLength));
        }
    }

    private void newArray(Instruction instruction, ArrayElementType elementType) {
        int length = constantLength(instruction);
        Value array = values.int32();
        context.emit(new IrInstruction.NewArray(array, elementType, length));
        context.arrays().add(new ArrayDeclaration(array, elementType, length));
        context.facts().markKnownArray(array, length);
        stack.push(array);
    }

    private void newMultiArray(Instruction instruction) {
        List<Integer> sizes = new ArrayList<>();
        for (int dimension = instruction.operandB() - 1; dimension >= 0; dimension--) {
            sizes.addFirst(constantLength(instruction));
        }
        String descriptor = context.constantPool().className(instruction.operandA());
        ArrayElementType leafType = ArrayElementType.fromDescriptor(descriptor.charAt(descriptor.length() - 1))
                .orElseThrow(() -> new CompileException(context.at(instruction)
                        + ": multidimensional object arrays are not supported yet: " + descriptor));
        Value array = values.int32();
        context.emit(new IrInstruction.NewMultiArray(array, leafType, List.copyOf(sizes)));
        context.facts().markKnownArray(array, sizes.getFirst());
        stack.push(array);
    }

    private int constantLength(Instruction site) {
        return stack.popLiteralInt().orElseThrow(() -> new CompileException(context.at(site)
                + ": array length must be a compile-time constant"));
    }
}
