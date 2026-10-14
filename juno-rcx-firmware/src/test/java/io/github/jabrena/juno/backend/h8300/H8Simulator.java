package io.github.jabrena.juno.backend.h8300;

/**
 * Runs H8/300 machine code for the instruction subset {@link H8Assembler} emits, so a firmware's logic can be tested
 * without a brick. A {@code jsr} below {@link #ROM_END} goes to the {@link Rom} instead of the code at that address.
 * Memory is the full 64 KB space; the stack starts where the RCX ROM leaves it, in on-chip RAM.
 */
final class H8Simulator {
    /** The RCX boot ROM covers the addresses below this one. */
    static final int ROM_END = 0x4000;
    private static final int STACK_TOP = 0xFF7E;

    /** A stand-in for the ROM routines; reads its arguments through the simulator and returns r6. */
    interface Rom {
        int call(int routine, H8Simulator cpu);
    }

    /** Thrown by a {@link Rom} to end a run, for example once the test has no more messages to deliver. */
    static final class Stop extends RuntimeException {
        Stop() {
            super(null, null, false, false);
        }
    }

    private final byte[] memory = new byte[0x10000];
    private final int[] registers = new int[8];
    private final Rom rom;
    private int pc;
    private boolean negative;
    private boolean zero;
    private boolean overflow;
    private boolean carry;

    H8Simulator(H8Image image, Rom rom) {
        this.rom = rom;
        byte[] bytes = image.bytes();
        System.arraycopy(bytes, 0, memory, image.origin(), bytes.length);
        pc = image.entry();
        registers[7] = STACK_TOP;
    }

    /** Runs until the ROM throws {@link Stop}, failing after {@code maxSteps} instructions. */
    void run(int maxSteps) {
        try {
            for (int step = 0; step < maxSteps; step++) {
                step();
            }
        } catch (Stop stop) {
            return;
        }
        throw new AssertionError("still running after " + maxSteps + " instructions, pc 0x" + Integer.toHexString(pc));
    }

    int register(int number) {
        return registers[number];
    }

    /** The {@code index}th 16-bit argument the caller pushed, 0 being the second argument of the routine. */
    int stackArgument(int index) {
        return readWord(registers[7] + 2 * index);
    }

    int readWord(int address) {
        return (memory[address & 0xFFFF] & 0xFF) << 8 | memory[(address + 1) & 0xFFFF] & 0xFF;
    }

    void writeWord(int address, int value) {
        memory[address & 0xFFFF] = (byte) (value >> 8);
        memory[(address + 1) & 0xFFFF] = (byte) value;
    }

    int readByte(int address) {
        return memory[address & 0xFFFF] & 0xFF;
    }

    void writeByte(int address, int value) {
        memory[address & 0xFFFF] = (byte) value;
    }

    private void step() {
        int first = fetch();
        int second = fetch();
        int high = second >> 4;
        int low = second & 0xF;
        switch (first) {
            case 0x79 -> setWord(low, fetchWord());
            case 0x0D -> setWord(low, registers[high]);
            case 0x6B -> {
                int address = fetchWord();
                if (high == 0) {
                    setWord(low, readWord(address));
                } else {
                    writeWord(address, registers[low]);
                    wordFlags(registers[low]);
                }
            }
            case 0x69 -> {
                if (high < 8) {
                    setWord(low, readWord(registers[high]));
                } else {
                    writeWord(registers[high & 7], registers[low]);
                }
            }
            case 0x6D -> {
                if (high == 0xF) {
                    registers[7] = (registers[7] - 2) & 0xFFFF;
                    writeWord(registers[7], registers[low]);
                } else {
                    setWord(low, readWord(registers[7]));
                    registers[7] = (registers[7] + 2) & 0xFFFF;
                }
            }
            case 0x09 -> registers[low] = arithmetic(registers[low], registers[high], false);
            case 0x19 -> registers[low] = arithmetic(registers[low], registers[high], true);
            case 0x1D -> arithmetic(registers[low], registers[high], true);
            case 0x0B -> registers[low & 7] = (registers[low & 7] + (high == 8 ? 2 : 1)) & 0xFFFF;
            case 0x1B -> registers[low & 7] = (registers[low & 7] - (high == 8 ? 2 : 1)) & 0xFFFF;
            case 0x5E -> call(fetchWord());
            case 0x5A -> pc = fetchWord();
            case 0x54 -> {
                pc = readWord(registers[7]);
                registers[7] = (registers[7] + 2) & 0xFFFF;
            }
            case 0x0C -> setByte(low, byteRegister(high));
            case 0x6A -> {
                int address = fetchWord();
                if (high == 0) {
                    setByte(low, readByte(address));
                } else {
                    writeByte(address, byteRegister(low));
                    byteFlags(byteRegister(low));
                }
            }
            case 0x6E -> {
                int address = registers[high & 7] + fetchWord();
                if (high < 8) {
                    setByte(low, readByte(address));
                } else {
                    writeByte(address, byteRegister(low));
                    byteFlags(byteRegister(low));
                }
            }
            case 0x6F -> {
                int address = registers[high & 7] + fetchWord();
                if (high < 8) {
                    setWord(low, readWord(address));
                } else {
                    writeWord(address, registers[low]);
                    wordFlags(registers[low]);
                }
            }
            case 0x17 -> setByte(low, ~byteRegister(low));
            case 0x51 -> {
                int dividend = registers[low];
                int divisor = byteRegister(high);
                int quotient = dividend / divisor;
                if (quotient > 0xFF) {
                    throw new AssertionError("divxu.b quotient overflow: " + dividend + " / " + divisor);
                }
                registers[low] = (dividend % divisor) << 8 | quotient;
            }
            default -> immediateOrBranch(first, second);
        }
    }

    private void immediateOrBranch(int first, int value) {
        int register = first & 0xF;
        switch (first >> 4) {
            case 0xF -> setByte(register, value);
            case 0xE -> setByte(register, byteRegister(register) & value);
            case 0xC -> setByte(register, byteRegister(register) | value);
            case 0xD -> setByte(register, byteRegister(register) ^ value);
            case 0xA -> {
                int a = byteRegister(register);
                int result = (a - value) & 0xFF;
                byteFlags(result);
                carry = value > a;
                overflow = ((a ^ value) & (a ^ result) & 0x80) != 0;
            }
            case 0x4 -> {
                if (condition(register)) {
                    pc = (pc + (byte) value) & 0xFFFF;
                }
            }
            default -> throw new AssertionError("unsupported H8/300 instruction 0x%02X%02X at 0x%04X"
                    .formatted(first, value, pc - 2));
        }
    }

    private void call(int target) {
        if (target < ROM_END) {
            registers[6] = rom.call(target, this) & 0xFFFF;
            return;
        }
        registers[7] = (registers[7] - 2) & 0xFFFF;
        writeWord(registers[7], pc);
        pc = target;
    }

    private boolean condition(int code) {
        return switch (code) {
            case 0x0 -> true;
            case 0x1 -> false;
            case 0x2 -> !carry && !zero;
            case 0x3 -> carry || zero;
            case 0x4 -> !carry;
            case 0x5 -> carry;
            case 0x6 -> !zero;
            case 0x7 -> zero;
            case 0x8 -> !overflow;
            case 0x9 -> overflow;
            case 0xA -> !negative;
            case 0xB -> negative;
            case 0xC -> negative == overflow;
            case 0xD -> negative != overflow;
            case 0xE -> !zero && negative == overflow;
            default -> zero || negative != overflow;
        };
    }

    private int arithmetic(int destination, int source, boolean subtract) {
        int operand = subtract ? (-source) & 0xFFFF : source;
        int sum = destination + operand;
        int result = sum & 0xFFFF;
        carry = subtract ? source > destination : sum > 0xFFFF;
        overflow = subtract ? ((destination ^ source) & (destination ^ result) & 0x8000) != 0
                : (~(destination ^ source) & (destination ^ result) & 0x8000) != 0;
        negative = (result & 0x8000) != 0;
        zero = result == 0;
        return result;
    }

    private void setWord(int register, int value) {
        registers[register] = value & 0xFFFF;
        wordFlags(registers[register]);
    }

    private void wordFlags(int value) {
        negative = (value & 0x8000) != 0;
        zero = (value & 0xFFFF) == 0;
        overflow = false;
    }

    private int byteRegister(int number) {
        int word = registers[number & 7];
        return number < 8 ? word >> 8 : word & 0xFF;
    }

    private void setByte(int number, int value) {
        int b = value & 0xFF;
        int word = registers[number & 7];
        registers[number & 7] = number < 8 ? b << 8 | word & 0xFF : word & 0xFF00 | b;
        byteFlags(b);
    }

    private void byteFlags(int value) {
        negative = (value & 0x80) != 0;
        zero = (value & 0xFF) == 0;
        overflow = false;
    }

    private int fetch() {
        int value = memory[pc] & 0xFF;
        pc = (pc + 1) & 0xFFFF;
        return value;
    }

    private int fetchWord() {
        return fetch() << 8 | fetch();
    }
}
