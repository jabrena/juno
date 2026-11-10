package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The regulated pair in the firmware: the commands and their replies, the control cycle against the {@link FakeRcx}
 * wheel model, and the safety paths (stop, stall, timeout, stale sensors, override and recovery).
 */
@DisplayName("RCX regulated pair firmware")
class RcxRegulatorFirmwareTest {
    private static final int ARM = 0x63;
    private static final int STATUS = 0x61;
    private static final int STOP = 0x62;
    private static final int SPEED_COMMAND = 0x64;
    private static final int ACCELERATION_COMMAND = 0x74;
    private static final int TARGET_LEFT = 0x33;
    private static final int TARGET_RIGHT = 0x43;
    private static final int START = 0x52;
    private static final int BRAKE_LEAD = 0x92;
    private static final int ON_OFF = 0x21;
    private static final int SET_INPUT_TYPE = 0x32;
    private static final int REMOTE_BUTTONS = 0xD2;
    private static final int SESSION = 1;
    /** Configuration bits 5-7 hold the maximum motor power: 7, full power, and no braking or inversion. */
    private static final int FULL_POWER_FLAGS = 0xE0;
    private static final int STEPS = 3_000_000;

    private static final int READY = 1;
    private static final int RUNNING = 2;
    private static final int COMPLETE = 3;
    private static final int STOPPED = 4;
    private static final int STALLED = 5;
    private static final int TIMED_OUT = 6;
    private static final int FAULT = 7;
    private static final int ACCEPTED = 0;
    private static final int DUPLICATE = 1;
    private static final int NOT_ARMED = 2;
    private static final int BUSY = 3;
    private static final int ID_CONFLICT = 7;
    private static final int STALE_COMMAND = 8;

    private static final int MODE_FORWARD = 1;
    private static final int MODE_BACKWARD = 2;
    private static final int MODE_BRAKE = 3;
    private static final int MODE_FLOAT = 4;
    private static final int LEFT = 0;
    private static final int RIGHT = 2;
    private static final int SENSOR_1_TYPE = 0xC080;
    private static final int SENSOR_2_TYPE = 0xC088;

    private final FakeRcx rcx = new FakeRcx();

    private H8Simulator run(int steps) {
        H8Simulator cpu = new H8Simulator(new RcxRemoteFirmware().image(), rcx);
        cpu.runFor(steps);
        return cpu;
    }

    /** The last reply to {@code command}, as its complemented opcode then its arguments. */
    private int[] lastReplyTo(int command) {
        int opcode = ~command & 0xFF;
        List<int[]> replies = rcx.replies();
        for (int index = replies.size() - 1; index >= 0; index--) {
            if (replies.get(index)[0] == opcode) {
                return replies.get(index);
            }
        }
        throw new AssertionError("no reply to 0x%02X".formatted(command));
    }

    /** Every reply to {@code command}, in order. */
    private List<int[]> repliesTo(int command) {
        int opcode = ~command & 0xFF;
        return rcx.replies().stream().filter(reply -> reply[0] == opcode).toList();
    }

    /** A position as the firmware reports it: a 16-bit word, low byte first, read as a signed step count. */
    private static int signed(int low, int high) {
        return (short) (low | high << 8);
    }

    @Nested
    @DisplayName("Commands and their replies")
    class Commands {

        @Test
        @DisplayName("Arm acknowledges the session with the protocol version and mapping 1, and reports ready")
        void shouldArmAndReportReady() {
            // Given
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(ARM)).containsExactly(0x9C, ACCEPTED, 1, 1);
            assertThat(lastReplyTo(STATUS)).containsExactly(0x9E, SESSION, 0, READY, 0, 0, 0, 0, 0);
        }

        @Test
        @DisplayName("Commands before arming are refused as not armed")
        void shouldRefuseCommandsBeforeArming() {
            // Given
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(SPEED_COMMAND)).containsExactly(0x9B, NOT_ARMED);
            assertThat(lastReplyTo(ACCELERATION_COMMAND)).containsExactly(0x8B, NOT_ARMED);
            assertThat(lastReplyTo(START)).containsExactly(0xAD, NOT_ARMED, SESSION, 1);
        }

        @Test
        @DisplayName("A profile with a zero speed or timeout is an invalid argument, and an oversized speed is out of range")
        void shouldRejectBadProfiles() {
            // Given
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 0, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(SPEED_COMMAND, SESSION, 0x00, 0x40, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 0);

            // When
            run(STEPS);

            // Then
            assertThat(repliesTo(SPEED_COMMAND).stream().map(reply -> reply[1]).toList())
                    .containsExactly(5, 6, 0);
            assertThat(repliesTo(ACCELERATION_COMMAND).stream().map(reply -> reply[1]).toList())
                    .containsExactly(0, 0, 5);
        }

        @Test
        @DisplayName("A stop for another motion is stale and leaves the motion alone")
        void shouldRefuseAStaleStop() {
            // Given
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(STOP, SESSION, 9);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(STOP)).containsExactly(0x9D, STALE_COMMAND, SESSION, 9);
        }
    }

    @Nested
    @DisplayName("Control cycle against the wheel model")
    class ControlCycle {

        @Test
        @DisplayName("A motion drives the left wheel forward and the right backward, and completes both wheels on target")
        void shouldCompleteAMotionOnBothWheels() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            // The model's wheels stop the instant they are braked, so nothing coasts and no early brake is due.
            rcx.receive(BRAKE_LEAD, SESSION, 0);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(400, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(COMPLETE);
            assertThat(signed(status[5], status[6])).isBetween(39, 41);
            assertThat(signed(status[7], status[8])).isBetween(-41, -39);
            assertThat(rcx.wheelPosition(LEFT)).isBetween(39, 41);
            assertThat(rcx.wheelPosition(RIGHT)).isBetween(-41, -39);
            assertThat(rcx.commandedMode(LEFT)).isIn(MODE_BRAKE, MODE_FLOAT);
        }

        @Test
        @DisplayName("At the lowest maximum power a motion still completes: the slow-down near the target keeps one level")
        void shouldCompleteAMotionAtTheLowestMaximumPower() {
            // Given: maximum power 1 (bits 5-7), so slowing a level near the target would mean braking short of it
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, 0x20);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(BRAKE_LEAD, SESSION, 0);
            rcx.receive(TARGET_LEFT, SESSION, 20, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 20, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(1500, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(COMPLETE);
            assertThat(rcx.wheelPosition(LEFT)).isBetween(20, 21);
            assertThat(rcx.wheelPosition(RIGHT)).isBetween(20, 21);
        }

        @Test
        @DisplayName("A status right after arming again reports the restarted positions, not the last motion's")
        void shouldReportRestartedPositionsRightAfterRearming() {
            // Given: a completed motion leaves both wheels away from zero, then the pair is armed again
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(400, ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(STATUS, SESSION);

            // When
            run(STEPS);

            // Then: the host builds its next targets on these positions, so they must be the restarted ones
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(READY);
            assertThat(signed(status[5], status[6])).isZero();
            assertThat(signed(status[7], status[8])).isZero();
        }

        @Test
        @DisplayName("A repeated start of the running motion is a duplicate that does not restart it")
        void shouldAnswerARepeatedStartAsDuplicate() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 0xFC, 0x03, 1); // the fastest: the duplicate, not the pace, is under test
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(5, TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(START)).containsExactly(0xAD, DUPLICATE, SESSION, 1);
            assertThat(lastReplyTo(STATUS)[3]).isEqualTo(COMPLETE);
        }

        @Test
        @DisplayName("A different start under the same motion ID is an ID conflict and changes nothing")
        void shouldRefuseAConflictingStart() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(5, TARGET_LEFT, SESSION, 90, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 1);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(START)).containsExactly(0xAD, ID_CONFLICT, SESSION, 1);
        }

        @Test
        @DisplayName("A start of a newer motion while one runs is busy")
        void shouldBeBusyWhileAMotionRuns() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(5, TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xD8, 0xFF);
            rcx.receive(START, SESSION, 2);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(START)).containsExactly(0xAD, BUSY, SESSION, 2);
        }

        @Test
        @DisplayName("A stop during a motion ends it on both wheels and reads stopped")
        void shouldStopAMotion() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(60, STOP, SESSION, 1);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(STOP)).containsExactly(0x9D, ACCEPTED, SESSION, 1);
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(STOPPED);
            assertThat(rcx.wheelPosition(LEFT)).isLessThan(400);
            assertThat(rcx.commandedMode(LEFT)).isIn(MODE_BRAKE, MODE_FLOAT);
            assertThat(rcx.commandedMode(RIGHT)).isIn(MODE_BRAKE, MODE_FLOAT);
        }

        @Test
        @DisplayName("A jammed wheel stalls, stops both outputs and latches the stall fault")
        void shouldStallAJammedWheel() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.jam(LEFT);
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(600, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(STALLED);
            assertThat(status[4] & 0x01).isEqualTo(0x01);
            assertThat(rcx.commandedMode(LEFT)).isIn(MODE_BRAKE, MODE_FLOAT);
        }

        @Test
        @DisplayName("A motion that outlasts its timeout stops on both wheels as timed out")
        void shouldTimeOutAMotion() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 5, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 1);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(600, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(TIMED_OUT);
            assertThat(status[4] & 0x10).isEqualTo(0x10);
        }

        @Test
        @DisplayName("Sensors that stop refreshing are reported stale and stop both wheels, not as a stall")
        void shouldReportStaleSensors() {
            // Given
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(400, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(FAULT);
            assertThat(status[4] & 0x0C).isEqualTo(0x0C);
        }

        @Test
        @DisplayName("A legacy command for output A while the pair runs latches the override and is not applied")
        void shouldLatchTheOverrideForALegacyCommand() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(10, ON_OFF, 0x80 | 0x01);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(ON_OFF)).containsExactly(0xDE);
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(FAULT);
            assertThat(status[4] & 0x20).isEqualTo(0x20);
        }

        @Test
        @DisplayName("A legacy command for output B alone goes on while the pair runs")
        void shouldLetALegacyCommandForOutputBThrough() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(10, ON_OFF, 0x80 | 0x02);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(rcx.calls()).contains("motor 2001 1 7");
            assertThat(lastReplyTo(STATUS)[3]).isEqualTo(COMPLETE);
        }

        @Test
        @DisplayName("Changing the type of a regulated input while the pair is owned is rejected")
        void shouldRejectSensorChangesWhileOwned() {
            // Given
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SET_INPUT_TYPE, 0, 1);
            rcx.receive(SET_INPUT_TYPE, 1, 1);

            // When
            H8Simulator cpu = run(STEPS);

            // Then
            assertThat(cpu.readByte(SENSOR_1_TYPE)).isEqualTo(4);
            assertThat(cpu.readByte(SENSOR_2_TYPE)).isEqualTo(1);
        }

        @Test
        @DisplayName("A move smaller than the deadband settles within tolerance instead of hunting")
        void shouldSettleASmallMoveWithinTheDeadband() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 3, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0xFD, 0xFF);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(STATUS)[3]).isEqualTo(COMPLETE);
            assertThat(rcx.wheelPosition(LEFT)).isBetween(2, 4);
            assertThat(rcx.wheelPosition(RIGHT)).isBetween(-4, -2);
        }

        @Test
        @DisplayName("The remote's stop button ends a running movement on both wheels")
        void shouldStopAMotionFromTheRemote() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(60, REMOTE_BUTTONS, 0x40, 0x00);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(STATUS)[3]).isEqualTo(STOPPED);
            assertThat(rcx.commandedMode(LEFT)).isIn(MODE_BRAKE, MODE_FLOAT);
        }

        @Test
        @DisplayName("Holding a remote button for output A while the pair runs is an external override")
        void shouldOverrideTheWheelsForARemoteButton() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 0x90, 0x01);
            rcx.receive(TARGET_RIGHT, SESSION, 0x90, 0x01);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(10, REMOTE_BUTTONS, 0x00, 0x08);
            rcx.receiveAfter(200, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            int[] status = lastReplyTo(STATUS);
            assertThat(status[3]).isEqualTo(FAULT);
            assertThat(status[4] & 0x20).isEqualTo(0x20);
        }

        @Test
        @DisplayName("Arming again clears a latched stall and returns to ready")
        void shouldRecoverByRearming() {
            // Given
            rcx.continuousAnalog();
            rcx.wheelPlant();
            rcx.jam(LEFT);
            rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receive(SPEED_COMMAND, SESSION, 100, 0, 1);
            rcx.receive(ACCELERATION_COMMAND, SESSION, 200, 0, 10);
            rcx.receive(TARGET_LEFT, SESSION, 40, 0);
            rcx.receive(TARGET_RIGHT, SESSION, 0, 0);
            rcx.receive(START, SESSION, 1);
            rcx.receiveAfter(600, ARM, SESSION, 1, FULL_POWER_FLAGS);
            rcx.receiveAfter(5, STATUS, SESSION);

            // When
            run(STEPS);

            // Then
            assertThat(lastReplyTo(ARM)).containsExactly(0x9C, ACCEPTED, 1, 1);
            assertThat(lastReplyTo(STATUS)[3]).isEqualTo(READY);
        }
    }
}
