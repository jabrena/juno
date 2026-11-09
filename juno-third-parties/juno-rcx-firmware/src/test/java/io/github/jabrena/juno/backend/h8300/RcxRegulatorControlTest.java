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
    private static final int SPEED_COMMAND = 0x64;
    private static final int ACCELERATION_COMMAND = 0x74;
    private static final int TARGET_LEFT = 0x33;
    private static final int TARGET_RIGHT = 0x43;
    private static final int START = 0x52;
    private static final int CYCLES = 0x71;
    private static final int SESSION = 1;
    private static final int FULL_POWER_FLAGS = 0xE0;
    private static final int STEPS = 3_000_000;
    /** About two counts per cycle and 0.4 per cycle squared at the firmware's rate, so a cycle's change is visible. */
    private static final int SPEED = 640;
    private static final int ACCELERATION = 128;
    /** The highest rate the brick accepts: about three counts per cycle, so the error clears the deadband at once. */
    private static final int FAST = RcxRegulator.MAX_RATE;
    private static final int LEFT = RcxRegulator.BASE + 0x20;
    private static final int RIGHT = RcxRegulator.BASE + 0x40;
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
        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
        rcx.receive(TARGET_RIGHT, SESSION, 0x70, 0xFE);
        rcx.receive(START, SESSION, 1);
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
    @DisplayName("Both wheels drive at the maximum power while they are level")
    void shouldDriveBothWheelsAtTheMaximumPowerWhileLevel() {
        // Given: a far target on both sides
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
        rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
        rcx.receive(START, SESSION, 1);
        H8Simulator cpu = boot(rcx);

        // When: twenty cycles run
        int cycles = 0;
        for (int step = 0; step < STEPS && cycles < 20; step++) {
            cpu.runFor(1);
            cycles = cpu.readWord(RcxRegulator.ELAPSED);
        }

        // Then: level wheels, both driven at full power
        assertThat(cpu.readByte(LEFT + RcxRegulator.POWER)).isEqualTo(MAX_POWER);
        assertThat(cpu.readByte(RIGHT + RcxRegulator.POWER)).isEqualTo(MAX_POWER);
        assertThat(rcx.wheelPosition(0) - rcx.wheelPosition(2)).isBetween(-1, 1);
    }

    @Test
    @DisplayName("A wheel two counts ahead of the other brakes until the other catches up")
    void shouldBrakeAWheelThatGetsAheadOfTheOther() {
        // Given: the right wheel is jammed, so the left gets ahead
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.jam(2);
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
        rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
        rcx.receive(START, SESSION, 1);
        H8Simulator cpu = boot(rcx);

        // When: a hundred cycles run, well before the right wheel's stall window
        int cycles = 0;
        for (int step = 0; step < STEPS && cycles < 100; step++) {
            cpu.runFor(1);
            cycles = cpu.readWord(RcxRegulator.ELAPSED);
        }

        // Then: the left wheel waits two counts ahead, braked at full braking power, instead of driving on: the ROM
        // brakes with the power it is given, so a brake at power 0 would let it coast
        assertThat(rcx.wheelPosition(0)).isBetween(2, 3);
        assertThat(cpu.readByte(LEFT + RcxRegulator.POWER)).isZero();
        assertThat(rcx.calls()).contains("motor 2000 3 7");
    }

    @Test
    @DisplayName("A timeout in seconds becomes that many seconds of 333 control cycles, and one past a word is refused")
    void shouldConvertTheTimeoutToControlCycles() {
        // Given: a ten-second timeout, then one too long to count in a word
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
        rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
        rcx.receive(START, SESSION, 1);
        rcx.receiveAfter(5, ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8,
                RcxRegulator.MAX_TIMEOUT_SECONDS + 1);
        H8Simulator cpu = boot(rcx);

        // When
        cpu.runFor(STEPS);

        // Then: 10 s is 3330 cycles; the second profile is refused, as running (busy) or out of range, never accepted
        assertThat(cpu.readWord(RcxRegulator.TIMEOUT_TICKS)).isEqualTo(10 * RcxRegulator.TICKS_PER_SECOND);
        List<int[]> replies = rcx.replies().stream().filter(reply -> reply[0] == 0x8B).toList();
        assertThat(replies).hasSize(2);
        assertThat(replies.get(1)[1]).isNotZero();
    }

    @Test
    @DisplayName("The cycle counter runs in any state and the diagnostic command reports it, low byte first")
    void shouldCountControlCyclesWhileIdle() {
        // Given: never armed, so only the counter runs; two reads some cycles apart
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.receiveAfter(50, CYCLES, SESSION);
        rcx.receiveAfter(50, CYCLES, SESSION);
        H8Simulator cpu = boot(rcx);

        // When
        cpu.runFor(STEPS);

        // Then: two replies (0x71 complemented is 0x8E), the second further on, both matching the counter's range
        List<int[]> replies = rcx.replies().stream().filter(reply -> reply[0] == 0x8E).toList();
        assertThat(replies).hasSize(2);
        int first = replies.get(0)[1] | replies.get(0)[2] << 8;
        int second = replies.get(1)[1] | replies.get(1)[2] << 8;
        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        assertThat(cpu.readWord(RcxRegulator.CYCLES_RUN)).isGreaterThanOrEqualTo(second);
    }

}
