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
    private static final int DRIVE = 0x81;
    private static final int BRAKE_LEAD = 0x92;
    /** Power 1 with the brake on stop, as the reference build drives. */
    private static final int LOW_POWER_BRAKE_FLAGS = 0x30;
    /** The coasting wheel's braking time constant, in control cycles, and the 31-count move of 500 mm. */
    private static final int COAST_CYCLES = 40;
    private static final int DISTANCE = 31;
    /** Control cycles left for the wheels to coast to rest once the motion completes. */
    private static final int SETTLE_CYCLES = 600;
    /** Each wheel's exact travel, in counts, at the end of the last {@link #coastedStop}. */
    private static final double[] LAST_TRAVEL = new double[2];
    private static final int COMPLETE_STATE = RcxRegulator.COMPLETE;
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
        rcx.receiveAfter(80, DRIVE, SESSION);
        H8Simulator cpu = boot(rcx);

        // When: a hundred cycles run, well before the right wheel's stall window
        int cycles = 0;
        for (int step = 0; step < STEPS && cycles < 100; step++) {
            cpu.runFor(1);
            cycles = cpu.readWord(RcxRegulator.ELAPSED);
        }

        // Then: the left wheel waits after its first count, since the right has not moved, braked at full braking power
        // instead of driving on (the ROM brakes with the power it is given, so a brake at power 0 would let it coast);
        // the diagnostic reports it unpowered (0x81 complemented is 0x7E) and held for cycles
        assertThat(rcx.wheelPosition(0)).isBetween(1, 2);
        assertThat(cpu.readByte(LEFT + RcxRegulator.POWER)).isZero();
        assertThat(rcx.calls()).contains("motor 2000 3 7");
        int[] drive = rcx.replies().stream().filter(reply -> reply[0] == 0x7E).findFirst().orElseThrow();
        assertThat(drive[1]).isZero();
        assertThat(drive[3] | drive[4] << 8).isPositive();
    }

    @Test
    @DisplayName("A slower left motor does not fall behind: the faster wheel waits, and both finish together")
    void shouldKeepAFasterWheelLevelWithASlowerOne() {
        // Given: the left motor turns its wheel at half the right's speed
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.weaken(0);
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        // The model's wheels stop the instant they are braked, so no early brake is due.
        rcx.receive(BRAKE_LEAD, SESSION, 0);
        rcx.receive(TARGET_LEFT, SESSION, 31, 0);
        rcx.receive(TARGET_RIGHT, SESSION, 31, 0);
        rcx.receive(START, SESSION, 1);
        H8Simulator cpu = boot(rcx);

        // When: the motion runs to completion, watching the gap between the wheels every cycle
        int worstGap = 0;
        int lastCycle = 0;
        for (int step = 0; step < STEPS && cpu.readByte(RcxRegulator.STATE) != COMPLETE_STATE; step++) {
            cpu.runFor(1);
            int cycle = cpu.readWord(RcxRegulator.ELAPSED);
            if (cycle != lastCycle) {
                lastCycle = cycle;
                worstGap = Math.max(worstGap, Math.abs(rcx.wheelPosition(0) - rcx.wheelPosition(2)));
            }
        }

        // Then: never more than two counts apart, and both on the target
        assertThat(cpu.readByte(RcxRegulator.STATE)).isEqualTo(COMPLETE_STATE);
        assertThat(worstGap).isLessThanOrEqualTo(2);
        assertThat(rcx.wheelPosition(0)).isBetween(31, 32);
        assertThat(rcx.wheelPosition(2)).isBetween(31, 32);
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

    /**
     * Runs a {@link #DISTANCE}-count move at power 1 on wheels that coast for {@link #COAST_CYCLES}, with the given
     * brake lead (none sent when negative, leaving the arm's default), and returns both wheels' positions at rest.
     */
    private static int[] coastedStop(int lead, boolean slowMotors) {
        return coastedStop(lead, slowMotors ? 0.5 : 1, slowMotors ? 0.5 : 1);
    }

    /** As {@link #coastedStop(int, boolean)}, with each wheel's speed scaled by its factor. */
    private static int[] coastedStop(int lead, double leftFactor, double rightFactor) {
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.coast(COAST_CYCLES);
        rcx.speedFactor(0, leftFactor);
        rcx.speedFactor(2, rightFactor);
        rcx.receive(ARM, SESSION, 1, LOW_POWER_BRAKE_FLAGS);
        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        if (lead >= 0) {
            rcx.receive(BRAKE_LEAD, SESSION, lead);
        }
        rcx.receive(TARGET_LEFT, SESSION, DISTANCE, 0);
        rcx.receive(TARGET_RIGHT, SESSION, DISTANCE, 0);
        rcx.receive(START, SESSION, 1);
        H8Simulator cpu = boot(rcx);
        for (int step = 0; step < STEPS && cpu.readByte(RcxRegulator.STATE) != COMPLETE_STATE; step++) {
            cpu.runFor(1);
        }
        assertThat(cpu.readByte(RcxRegulator.STATE)).isEqualTo(COMPLETE_STATE);
        int completedAt = cpu.readWord(RcxRegulator.CYCLES_RUN);
        for (int step = 0; step < STEPS && cpu.readWord(RcxRegulator.CYCLES_RUN) - completedAt < SETTLE_CYCLES; step++) {
            cpu.runFor(1);
        }
        LAST_TRAVEL[0] = rcx.wheelTravel(0);
        LAST_TRAVEL[1] = rcx.wheelTravel(2);
        return new int[] {rcx.wheelPosition(0), rcx.wheelPosition(2)};
    }

    @Test
    @DisplayName("Without a brake lead, wheels that coast after the brake run several counts past the target")
    void shouldOvershootWithoutABrakeLead() {
        // When: the early brake is off
        int[] positions = coastedStop(0, false);

        // Then: about the speed times the coast, 5 counts, past 31
        assertThat(positions[0]).isGreaterThanOrEqualTo(DISTANCE + 3);
        assertThat(positions[1]).isGreaterThanOrEqualTo(DISTANCE + 3);
    }

    @Test
    @DisplayName("A brake lead that matches the coast stops both wheels within a count of the target")
    void shouldStopOnTheTargetWithAMatchingBrakeLead() {
        // When
        int[] positions = coastedStop(COAST_CYCLES, false);

        // Then
        assertThat(positions[0]).isBetween(DISTANCE - 1, DISTANCE + 1);
        assertThat(positions[1]).isBetween(DISTANCE - 1, DISTANCE + 1);
    }

    @Test
    @DisplayName("The same brake lead still stops on the target when the motors run at half the speed")
    void shouldScaleTheEarlyBrakeWithTheWheelSpeed() {
        // When: slower motors, as on weaker batteries, coast half as far
        int[] positions = coastedStop(COAST_CYCLES, true);

        // Then
        assertThat(positions[0]).isBetween(DISTANCE - 1, DISTANCE + 1);
        assertThat(positions[1]).isBetween(DISTANCE - 1, DISTANCE + 1);
    }

    @Test
    @DisplayName("Braking on the predicted time stops within half a step of the target at any motor speed")
    void shouldBrakeOnThePredictedTimeRatherThanWholeSteps() {
        // Given: motor speeds from 0.70 to 1.00 of nominal, 8 to 11.4 cycles a step, with the lead matching the coast,
        // so the ideal stop is the start of step 31 whatever the speed. Braking on whole steps rounds the lead down to
        // a multiple of the step time, so it brakes late by up to a step (31.73 at 0.76); braking on the predicted time
        // rounds it to a cycle.
        double nearest = Double.MAX_VALUE;
        double farthest = 0;
        for (int percent = 70; percent <= 100; percent += 3) {
            // When
            coastedStop(COAST_CYCLES, percent / 100.0, percent / 100.0);

            // Then: the wheels' exact travel, before the sensor rounds it to a step
            for (double travel : LAST_TRAVEL) {
                nearest = Math.min(nearest, travel);
                farthest = Math.max(farthest, travel);
            }
        }
        assertThat(nearest).isGreaterThan(DISTANCE - 0.5);
        assertThat(farthest).isLessThan(DISTANCE + 0.5);
        assertThat(farthest - nearest).as("spread over the speeds, in counts").isLessThan(0.6);
    }

    @Test
    @DisplayName("Arming sets the default brake lead, the lead command sets another, and it is refused while running")
    void shouldSetTheBrakeLeadOnlyWhileIdle() {
        // Given: a lead of 20 before the start, and one of 30 while the motion runs
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receiveAfter(5, STATUS, SESSION);
        H8Simulator cpu = boot(rcx);
        cpu.runFor(STEPS);
        int armed = cpu.readByte(RcxRegulator.BRAKE_LEAD);

        rcx.receive(SPEED_COMMAND, SESSION, SPEED & 0xFF, SPEED >> 8, 1);
        rcx.receive(ACCELERATION_COMMAND, SESSION, ACCELERATION & 0xFF, ACCELERATION >> 8, 10);
        rcx.receive(BRAKE_LEAD, SESSION, 20);
        rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
        rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
        rcx.receive(START, SESSION, 1);
        rcx.receiveAfter(5, BRAKE_LEAD, SESSION, 30);

        // When
        cpu.runFor(STEPS);

        // Then: 0x92 complemented is 0x6D; accepted (0), then busy (3), and the lead stays 20
        assertThat(armed).isEqualTo(RcxRegulator.DEFAULT_BRAKE_LEAD);
        List<int[]> replies = rcx.replies().stream().filter(reply -> reply[0] == 0x6D).toList();
        assertThat(replies).hasSize(2);
        assertThat(replies.get(0)[1]).isEqualTo(RcxRegulator.ACCEPTED);
        assertThat(replies.get(1)[1]).isEqualTo(RcxRegulator.BUSY);
        assertThat(cpu.readByte(RcxRegulator.BRAKE_LEAD)).isEqualTo(20);
    }
}
