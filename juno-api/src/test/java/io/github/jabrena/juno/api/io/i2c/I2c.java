package io.github.jabrena.juno.api.io.i2c;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only stand-in for the I2C bus intrinsic. Each register write is recorded as {address, register, value}. Reads
 * come from {@link #registers}, keyed by address and register; a register a test does not set answers -1, like a device
 * that does not acknowledge.
 */
public final class I2c {
    /** Every write, in order, as {address, register, value}. */
    public static final List<int[]> writes = new ArrayList<>();
    /** The value each readable register holds, keyed by {@code address << 8 | register}. */
    public static final Map<Integer, Integer> registers = new HashMap<>();
    public static int begins;

    private I2c() {
    }

    public static void reset() {
        writes.clear();
        registers.clear();
        begins = 0;
    }

    /** Sets what reading {@code register} on the device at {@code address} returns. */
    public static void device(int address, int register, int value) {
        registers.put(address << 8 | register, value);
    }

    public static void begin() {
        begins++;
    }

    public static void writeRegister(int address, int register, int value) {
        writes.add(new int[] {address, register, value});
    }

    public static int readRegister(int address, int register) {
        return registers.getOrDefault(address << 8 | register, -1);
    }

    public static int readRegister16(int address, int register) {
        return registers.getOrDefault(address << 8 | register, -1);
    }
}
