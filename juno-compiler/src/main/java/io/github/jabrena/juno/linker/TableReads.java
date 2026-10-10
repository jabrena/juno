package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;

import java.util.List;

/**
 * Whether one read of a {@link ConstantTables} candidate leaves it read-only: flash cannot be written by an
 * ordinary store, so the array a {@code getstatic} pushes may only ever reach an array load or
 * {@code arraylength}.
 */
final class TableReads {
    private static final int GETSTATIC = 178;
    private static final int ARRAYLENGTH = 190;
    private static final int[][] SIMPLE_EFFECTS = simpleEffects();

    private TableReads() {
    }

    /**
     * Whether the array {@code getstatic} at {@code index} pushes is consumed only as the array operand of an
     * array load or {@code arraylength}. Walks forward through straight-line code that may compute the index
     * (loads, constants, arithmetic, conversions, field reads, nested array loads and calls) while tracking
     * how many stack slots sit above the table; any other instruction, or one that would pop the table, fails.
     */
    static boolean readOnly(LinkedMethod linked, int index) {
        List<Instruction> instructions = linked.instructions();
        int above = 0;
        for (int cursor = index + 1; cursor < instructions.size(); cursor++) {
            Instruction instruction = instructions.get(cursor);
            int opcode = instruction.opcode();
            if (opcode >= 46 && opcode <= 53 && above == 1 || opcode == ARRAYLENGTH && above == 0) {
                return true;
            }
            int[] effect = stackEffect(linked, instruction);
            if (effect == null || effect[0] > above) {
                return false;
            }
            above += effect[1] - effect[0];
        }
        return false;
    }

    /**
     * {@code {popped, pushed}} slots for an instruction an index expression may contain (loads, constants,
     * arithmetic, conversions, comparisons, field reads, array loads and calls), else {@code null}.
     */
    private static int[] stackEffect(LinkedMethod linked, Instruction instruction) {
        ConstantPool pool = linked.owner().constantPool();
        return switch (instruction.opcode()) {
            case GETSTATIC -> new int[] {0, Descriptor.jvmSlots(pool.fieldRef(instruction.operandA()).descriptor())};
            case 180 -> new int[] {1, Descriptor.jvmSlots(pool.fieldRef(instruction.operandA()).descriptor())};
            case 182, 183, 184, 185 -> {
                Descriptor called = Descriptor.parse(pool.methodRef(instruction.operandA()).descriptor());
                int receiver = instruction.opcode() == 184 ? 0 : 1;
                yield new int[] {called.parameters().stream().mapToInt(Descriptor::jvmSlots).sum() + receiver,
                        Descriptor.jvmSlots(called.returnType())};
            }
            default -> SIMPLE_EFFECTS[instruction.opcode()];
        };
    }

    private static int[][] simpleEffects() {
        int[][] effects = new int[256][];
        // Constants and local loads; the long/double ones push two slots.
        for (int opcode = 1; opcode <= 45; opcode++) {
            effects[opcode] = new int[] {0, 1};
        }
        for (int opcode : new int[] {9, 10, 14, 15, 20, 22, 24, 30, 31, 32, 33, 38, 39, 40, 41}) {
            effects[opcode] = new int[] {0, 2};
        }
        for (int opcode = 46; opcode <= 53; opcode++) {
            effects[opcode] = new int[] {2, opcode == 47 || opcode == 49 ? 2 : 1};
        }
        // Binary arithmetic (iadd..drem) and bitwise (iand..lxor) alternate int-sized and long/double-sized
        // operands; so do negation (ineg..dneg) and the shifts, whose long forms take an int shift distance.
        for (int opcode = 96; opcode <= 131; opcode++) {
            boolean wide = opcode % 2 == 1;
            effects[opcode] = wide ? new int[] {4, 2} : new int[] {2, 1};
        }
        for (int opcode = 116; opcode <= 119; opcode++) {
            effects[opcode] = opcode % 2 == 1 ? new int[] {2, 2} : new int[] {1, 1};
        }
        for (int opcode = 120; opcode <= 125; opcode++) {
            effects[opcode] = opcode % 2 == 1 ? new int[] {3, 2} : new int[] {2, 1};
        }
        effects[132] = new int[] {0, 0};
        String conversions = "IJ IF ID JI JF JD FI FJ FD DI DJ DF IB IC IS";
        for (int opcode = 133; opcode <= 147; opcode++) {
            int at = (opcode - 133) * 3;
            effects[opcode] = new int[] {Descriptor.jvmSlots(conversions.substring(at, at + 1)),
                    Descriptor.jvmSlots(conversions.substring(at + 1, at + 2))};
        }
        effects[148] = new int[] {4, 1};
        effects[149] = new int[] {2, 1};
        effects[150] = new int[] {2, 1};
        effects[151] = new int[] {4, 1};
        effects[152] = new int[] {4, 1};
        effects[ARRAYLENGTH] = new int[] {1, 1};
        effects[192] = new int[] {1, 1};
        return effects;
    }
}
