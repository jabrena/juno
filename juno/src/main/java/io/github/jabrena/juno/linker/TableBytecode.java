package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;

import java.util.List;

/** javac's constant array initializer, as {@link ConstantTables} recognizes it in a {@code <clinit>}. */
final class TableBytecode {
    private static final int PUTSTATIC = 179;
    private static final int DUP = 89;
    private static final int NEWARRAY = 188;

    /** An initializer's element values (raw bits), and the index of the {@code putstatic} storing the array. */
    record Initializer(long[] values, int putstatic) {
    }

    private TableBytecode() {
    }

    /**
     * javac's array-initializer shape starting at {@code start}:
     * {@code <length> newarray (dup <index> <value> Xastore){length} putstatic}, all constants, or {@code null}.
     * javac stores every element of an {@code {...}} initializer, zeros included; fewer stores is a
     * {@code new T[n]} buffer, which is meant to be written and stays in the arena.
     */
    static Initializer initializer(List<Instruction> instructions, ConstantPool pool, int start) {
        if (start + 2 >= instructions.size() || instructions.get(start + 1).opcode() != NEWARRAY) {
            return null;
        }
        Long length = constant(pool, instructions.get(start), 'I');
        char element = elementOfAtype(instructions.get(start + 1).operandA());
        if (length == null || length < 0 || element == 0) {
            return null;
        }
        long[] values = new long[length.intValue()];
        boolean[] stored = new boolean[values.length];
        int cursor = start + 2;
        // javac stores every element; the Eclipse compiler (used by IDE builds) leaves out the zero ones, which the
        // new array already holds. Either way: dup, index, value, store, each index at most once.
        while (cursor < instructions.size() && instructions.get(cursor).opcode() == DUP) {
            if (cursor + 3 >= instructions.size() || instructions.get(cursor + 3).opcode() != storeOpcode(element)) {
                return null;
            }
            Long index = constant(pool, instructions.get(cursor + 1), 'I');
            Long value = constant(pool, instructions.get(cursor + 2), element);
            if (index == null || value == null || index < 0 || index >= values.length || stored[index.intValue()]) {
                return null;
            }
            values[index.intValue()] = value;
            stored[index.intValue()] = true;
            cursor += 4;
        }
        if (values.length > 0 && cursor == start + 2) {
            // new T[n] with no stores is a buffer to fill at run time, not a constant table.
            return null;
        }
        return cursor < instructions.size() && instructions.get(cursor).opcode() == PUTSTATIC
                ? new Initializer(values, cursor) : null;
    }

    /** {@code newarray}'s atype (4..11) as its element descriptor; boolean and byte share {@code bastore}. */
    static char elementOfAtype(int atype) {
        return atype >= 4 && atype <= 11 ? "BCFDBSIJ".charAt(atype - 4) : 0;
    }

    /** {@code iastore} (79) through {@code sastore} (86), with {@code aastore} (83) never matching. */
    private static int storeOpcode(char element) {
        return 79 + "IJFD-BCS".indexOf(element);
    }

    /** The constant {@code instruction} pushes, narrowed as storing it into an {@code element} array would. */
    private static Long constant(ConstantPool pool, Instruction instruction, char element) {
        return switch (element) {
            case 'J' -> longConstant(pool, instruction);
            case 'F' -> floatConstant(pool, instruction);
            case 'D' -> doubleConstant(pool, instruction);
            default -> {
                Integer value = intConstant(pool, instruction);
                yield value == null ? null : (long) switch (element) {
                    case 'B' -> (byte) value.intValue();
                    case 'C' -> (char) value.intValue();
                    case 'S' -> (short) value.intValue();
                    default -> value;
                };
            }
        };
    }

    private static Integer intConstant(ConstantPool pool, Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode >= 2 && opcode <= 8) {
            return opcode - 3;
        }
        if (opcode == 16 || opcode == 17) {
            return instruction.operandA();
        }
        boolean ldc = opcode == 18 || opcode == 19;
        return ldc && !pool.isFloat(instruction.operandA()) && !pool.isString(instruction.operandA())
                ? pool.integer(instruction.operandA()) : null;
    }

    private static Long longConstant(ConstantPool pool, Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode == 9 || opcode == 10) {
            return (long) (opcode - 9);
        }
        return opcode == 20 && !pool.isDouble(instruction.operandA()) ? pool.longValue(instruction.operandA()) : null;
    }

    private static Long floatConstant(ConstantPool pool, Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode >= 11 && opcode <= 13) {
            return (long) Float.floatToRawIntBits(opcode - 11);
        }
        boolean ldc = opcode == 18 || opcode == 19;
        return ldc && pool.isFloat(instruction.operandA())
                ? (long) Float.floatToRawIntBits(pool.floatValue(instruction.operandA())) : null;
    }

    private static Long doubleConstant(ConstantPool pool, Instruction instruction) {
        int opcode = instruction.opcode();
        if (opcode == 14 || opcode == 15) {
            return Double.doubleToRawLongBits(opcode - 14);
        }
        return opcode == 20 && pool.isDouble(instruction.operandA())
                ? Double.doubleToRawLongBits(pool.doubleValue(instruction.operandA())) : null;
    }
}
