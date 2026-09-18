package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Arrays;
import java.util.List;

public record IrMethod(MethodRef reference, int maxLocals, List<Value> values,
                       List<ArrayDeclaration> arrayDeclarations, List<IrBasicBlock> blocks) {
    public IrMethod {
        values = List.copyOf(values);
        arrayDeclarations = List.copyOf(arrayDeclarations);
        blocks = List.copyOf(blocks);
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).id() != index) {
                throw new IllegalArgumentException("IR values must be ordered and contiguous: expected id "
                        + index + ", got " + values.get(index).id());
            }
        }
        for (ArrayDeclaration declaration : arrayDeclarations) {
            requireDeclared(values, declaration.handle());
        }
        for (IrBasicBlock block : blocks) {
            for (IrInstruction instruction : block.instructions()) {
                for (Value value : IrValues.of(instruction)) {
                    requireDeclared(values, value);
                }
            }
            for (Value value : IrValues.of(block.terminator())) {
                requireDeclared(values, value);
            }
        }
    }

    /** Builds a method value table from the typed values referenced by freshly lowered IR. */
    public static IrMethod withInferredValues(MethodRef reference, int maxLocals, int valueCount,
                                              List<ArrayDeclaration> arrayDeclarations,
                                              List<IrBasicBlock> blocks) {
        Value[] inferred = new Value[valueCount];
        for (ArrayDeclaration declaration : arrayDeclarations) {
            register(inferred, declaration.handle());
        }
        for (IrBasicBlock block : blocks) {
            for (IrInstruction instruction : block.instructions()) {
                IrValues.of(instruction).forEach(value -> register(inferred, value));
            }
            IrValues.of(block.terminator()).forEach(value -> register(inferred, value));
        }
        for (int index = 0; index < inferred.length; index++) {
            if (inferred[index] == null) {
                // Lowering may consume and remove an int Const/StoreLocal pair at compile time (currently
                // fixed-size array lengths), leaving a deliberate gap while later ids stay deterministic.
                inferred[index] = Value.int32(index);
            }
        }
        return new IrMethod(reference, maxLocals, Arrays.asList(inferred), arrayDeclarations, blocks);
    }

    private static void requireDeclared(List<Value> values, Value value) {
        if (value.id() >= values.size() || !values.get(value.id()).equals(value)) {
            throw new IllegalArgumentException("IR value " + value + " is missing from the method value table");
        }
    }

    private static void register(Value[] values, Value value) {
        if (value.id() >= values.length) {
            throw new IllegalArgumentException("IR value id exceeds declared count: " + value);
        }
        Value existing = values[value.id()];
        if (existing != null && !existing.equals(value)) {
            throw new IllegalArgumentException("Conflicting IR types for value id " + value.id()
                    + ": " + existing.type() + " and " + value.type());
        }
        values[value.id()] = value;
    }
}
