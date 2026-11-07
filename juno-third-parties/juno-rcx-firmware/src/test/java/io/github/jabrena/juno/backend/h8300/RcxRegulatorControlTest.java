package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The regulator's control cycle against the {@link FakeRcx} wheel model: a fresh boot, the cycle's pairing of the two
 * wheels, the sensor configuration of the wheels' inputs, the velocity ramp, and the power law with its slew limit.
 */
@DisplayName("RCX regulated pair control cycle")
class RcxRegulatorControlTest {
    private static final int ARM = 0x63;
    private static final int STATUS = 0x61;
    private static final int PROFILE = 0x67;
    private static final int START = 0x76;
    private static final int SESSION = 1;
    private static final int FULL_POWER_FLAGS = 0xE0;
    private static final int STEPS = 3_000_000;
    private static final int SPEED = 100;
    private static final int ACCELERATION = 40;
    private static final int LEFT = RcxRegulator.BASE + 0x20;
    private static final int SENSOR_SIZE = 8;
    private static final int MAX_POWER = 7;

    private static H8Simulator boot(FakeRcx rcx) {
        return new H8Simulator(new RcxRemoteFirmware().image(), rcx);
    }

    /** The motor commands that drive a wheel, forward or backward: "motor 2000 1 7" and the like. */
    private static List<String> drivingCalls(FakeRcx rcx) {
        return rcx.calls().stream().filter(call -> call.matches("motor 200[02] [12] \\d+")).toList();
    }

    private static int[] lastReplyTo(FakeRcx rcx, int command) {
        int opcode = ~command & 0xFF;
        return rcx.replies().stream().filter(reply -> reply[0] == opcode).reduce((first, second) -> second)
                .orElseThrow();
    }

    /** The power the regulator must apply: the proportional law on the error, clamped, then slew-limited. */
    static int expectedPower(int error, int previous) {
        int proportional = (Math.abs(error) + 2) / RcxRegulator.KP_DIVISOR;
        int clamped = Math.min(Math.max(proportional, RcxRegulator.MIN_START_POWER), MAX_POWER);
        int slewCap = Math.max(previous + RcxRegulator.SLEW_PER_TICK, RcxRegulator.MIN_START_POWER);
        return Math.min(clamped, slewCap);
    }

    @Test
    @DisplayName("A fresh boot is not armed, its regulator state is clear, and no wheel is driven")
    void shouldBootNotArmedAndDriveNothing() {
        // Given
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(STATUS, SESSION);

        // When
        boot(rcx).runFor(STEPS);

        // Then
        assertThat(lastReplyTo(rcx, STATUS)).containsExactly(0x9E, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(drivingCalls(rcx)).isEmpty();
    }

    @Test
    @DisplayName("Both wheels are driven at most once per control cycle, and the two are driven in the same cycle")
    void shouldDriveBothWheelsInTheSameCycle() {
        // Given
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, SPEED, 0, ACCELERATION, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0x70, 0xFE);
        rcx.receiveAfter(2000, STATUS, SESSION);

        // When
        boot(rcx).runFor(STEPS);

        // Then: split the commands at each cycle's marker, and count each wheel's drive commands in the cycle
        int cycles = 0;
        int bothDriven = 0;
        int left = 0;
        int right = 0;
        boolean open = false;
        for (String call : rcx.calls()) {
            if (call.equals("tick")) {
                if (open) {
                    assertThat(left).isLessThanOrEqualTo(1);
                    assertThat(right).isLessThanOrEqualTo(1);
                    cycles++;
                    if (left == 1 && right == 1) {
                        bothDriven++;
                    }
                }
                left = 0;
                right = 0;
                open = true;
            } else if (call.matches("motor 2000 [12] \\d+")) {
                left++;
            } else if (call.matches("motor 2002 [12] \\d+")) {
                right++;
            }
        }
        assertThat(cycles).isGreaterThan(20);
        assertThat(bothDriven).isGreaterThan(10);
    }

    @Test
    @DisplayName("Arming configures inputs 1 and 3 as zeroed rotation sensors and leaves input 2 as it was")
    void shouldConfigureTheWheelSensorsAsRotation() {
        // Given: the same boot with and without the arm, to compare input 2
        H8Simulator unarmed = boot(new FakeRcx());
        unarmed.runFor(STEPS);

        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receiveAfter(50, STATUS, SESSION);

        // When
        H8Simulator armed = boot(rcx);
        armed.runFor(STEPS);

        // Then: type 4 is rotation and mode 0xE0 is LEGO's rotation mode, on inputs 0 and 2 (sensors 1 and 3)
        int sensors = RcxSensorCommands.SENSORS;
        assertThat(armed.readByte(sensors)).isEqualTo(4);
        assertThat(armed.readByte(sensors + 1)).isEqualTo(0xE0);
        assertThat(armed.readByte(sensors + 2 * SENSOR_SIZE)).isEqualTo(4);
        assertThat(armed.readByte(sensors + 2 * SENSOR_SIZE + 1)).isEqualTo(0xE0);
        int middle = sensors + SENSOR_SIZE;
        assertThat(armed.readByte(middle)).isEqualTo(unarmed.readByte(middle));
        assertThat(armed.readByte(middle + 1)).isEqualTo(unarmed.readByte(middle + 1));
        assertThat(rcx.calls()).contains(String.format("read %X", RcxRom.SENSOR_1));
        assertThat(rcx.calls()).contains(String.format("read %X", RcxRom.SENSOR_1 + 2));
    }

    @Test
    @DisplayName("The velocity rises by the acceleration each cycle until it reaches the speed, then holds")
    void shouldRampTheVelocityThenHoldIt() {
        // Given
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, SPEED, 0, ACCELERATION, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0x70, 0xFE);
        rcx.receiveAfter(2000, STATUS, SESSION);
        H8Simulator cpu = boot(rcx);

        // When: sample after every cycle, once the cycle counter moves
        int cycles = 0;
        int checked = 0;
        int lastCycle = 0;
        for (int step = 0; step < STEPS && cycles < 10; step++) {
            cpu.runFor(1);
            cycles = cpu.readWord(RcxRegulator.ELAPSED);
            if (cycles != lastCycle) {
                lastCycle = cycles;
                int velocity = cpu.readWord(LEFT + RcxRegulator.VELOCITY);
                int expected = Math.min(cycles * (ACCELERATION / 20), SPEED / 20);
                assertThat(velocity).as("velocity after cycle %d", cycles).isEqualTo(expected);
                checked++;
            }
        }

        // Then
        assertThat(checked).isGreaterThan(8);
    }

    @Test
    @DisplayName("The power follows the proportional law on the error, with the slew limit on the way up")
    void shouldDrivePowerFromTheErrorWithTheSlewLimit() {
        // Given: the left wheel is jammed, so its error grows with the trajectory
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.jam(0);
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, SPEED, 0, ACCELERATION, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0, 0);
        rcx.receiveAfter(2000, STATUS, SESSION);
        H8Simulator cpu = boot(rcx);

        // When / Then: after each cycle, the commanded power is the law's value for the error that cycle
        int cycles = 0;
        int lastCycle = 0;
        int previous = 0;
        int checked = 0;
        for (int step = 0; step < STEPS && cycles < 12; step++) {
            cpu.runFor(1);
            cycles = cpu.readWord(RcxRegulator.ELAPSED);
            if (cycles != lastCycle) {
                lastCycle = cycles;
                int trajectory = (short) cpu.readWord(LEFT + RcxRegulator.TRAJECTORY);
                int position = (short) cpu.readWord(LEFT + RcxRegulator.POSITION);
                int power = cpu.readByte(LEFT + RcxRegulator.POWER);
                assertThat(power).as("power after cycle %d", cycles)
                        .isEqualTo(expectedPower(trajectory - position, previous));
                previous = power;
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(8);
    }
}
