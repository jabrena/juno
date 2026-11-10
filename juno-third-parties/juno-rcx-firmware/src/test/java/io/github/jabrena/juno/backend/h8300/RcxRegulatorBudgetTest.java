package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The regulator's budgets on the brick: its RAM must not overlap any other firmware state, and the stack it uses during
 * a regulated run must stay within {@link #STACK_BUDGET_BYTES}. The stack measured here is the firmware's own calls; the
 * ROM's routines run on the same stack and are not modelled.
 */
@DisplayName("RCX regulated pair budgets")
class RcxRegulatorBudgetTest {
    private static final int TARGET_LEFT = 0x33;
    private static final int TARGET_RIGHT = 0x43;
    private static final int START = 0x52;
    private static final int SPEED_COMMAND = 0x64;
    private static final int ACCELERATION_COMMAND = 0x74;

    /** The RAM the regulator uses: its state word through the right side's last field. */
    private static final int REGULATOR_RAM = 0x60;
    private static final int RAM_START = 0xC000;
    private static final int RAM_END = 0xD000;
    private static final int STACK_TOP = 0xFF7E;
    /** The deepest the regulated run goes: 12 bytes measured, with a margin of 16 bytes for the firmware's own growth. */
    private static final int STACK_BUDGET_BYTES = 28;
    private static final int STEPS = 3_000_000;

    @Test
    @DisplayName("The regulator's RAM overlaps no other firmware state")
    void shouldKeepItsRamApartFromTheRestOfTheFirmware() throws IllegalAccessException {
        int start = RcxRegulator.BASE;
        int end = RcxRegulator.BASE + REGULATOR_RAM;
        List<Class<?>> firmware = List.of(RcxRemoteFirmware.class, RcxMotorCommands.class, RcxSensorCommands.class,
                RcxDisplayIcons.class, RcxDisplayText.class, RcxChannel.class, RcxBrickButtons.class,
                RcxRemoteControl.class, RcxCode.class);
        for (Class<?> owner : firmware) {
            for (Field field : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                    continue;
                }
                field.setAccessible(true);
                int address = field.getInt(null);
                if (address >= RAM_START && address < RAM_END) {
                    assertThat(address < start || address >= end)
                            .as("%s.%s at 0x%04X lies in the regulator's RAM".formatted(
                                    owner.getSimpleName(), field.getName(), address))
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("A regulated run stays within the stack budget")
    void shouldKeepTheStackWithinBudget() {
        // Given: a motion, a remote drive and a legacy command during the motion, then a stop
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(0x63, 1, 1, 0xE0);
        rcx.receive(SPEED_COMMAND, 1, 100, 0, 1);
        rcx.receive(ACCELERATION_COMMAND, 1, 200, 0, 10);
        rcx.receive(TARGET_LEFT, 1, 0x90, 0x01);
        rcx.receive(TARGET_RIGHT, 1, 0x90, 0x01);
        rcx.receive(START, 1, 1);
        rcx.receiveAfter(20, 0xD2, 0x00, 0x02);
        rcx.receiveAfter(40, 0x21, 0x80 | 0x02);
        rcx.receiveAfter(60, 0x62, 1, 1);
        rcx.receiveAfter(80, 0x61, 1);
        H8Simulator cpu = new H8Simulator(new RcxRemoteFirmware().image(), rcx);

        // When: the stack pointer is sampled after every instruction
        int lowest = STACK_TOP;
        for (int step = 0; step < STEPS; step++) {
            cpu.runFor(1);
            lowest = Math.min(lowest, cpu.register(7) & 0xFFFF);
        }

        // Then
        int depth = STACK_TOP - lowest;
        assertThat(depth).as("stack depth in bytes").isLessThanOrEqualTo(STACK_BUDGET_BYTES);
    }
}
