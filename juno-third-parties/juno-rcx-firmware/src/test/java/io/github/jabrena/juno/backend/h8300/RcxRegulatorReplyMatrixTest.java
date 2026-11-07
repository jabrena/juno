package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rejection the regulated pair can answer, and the configuration bits that set its brake policy and maximum power.
 * Each case is one command sequence against the {@link FakeRcx} wheel model.
 */
@DisplayName("RCX regulated pair replies")
class RcxRegulatorReplyMatrixTest {
    private static final int ARM = 0x63;
    private static final int STATUS = 0x61;
    private static final int STOP = 0x62;
    private static final int PROFILE = 0x67;
    private static final int START = 0x76;
    private static final int SESSION = 1;
    private static final int STEPS = 3_000_000;
    private static final int FULL_POWER_FLAGS = 0xE0;

    private static final int ACCEPTED = 0;
    private static final int DUPLICATE = 1;
    private static final int NOT_ARMED = 2;
    private static final int INVALID_SESSION = 4;
    private static final int INVALID_ARGUMENT = 5;
    private static final int OUT_OF_RANGE = 6;
    private static final int STALE_COMMAND = 8;
    private static final int BUSY = 3;
    private static final int MODE_BRAKE = 3;
    private static final int LEFT = 0;

    private final FakeRcx rcx = new FakeRcx();

    private H8Simulator run() {
        H8Simulator cpu = new H8Simulator(new RcxRemoteFirmware().image(), rcx);
        cpu.runFor(STEPS);
        return cpu;
    }

    /** Every reply to {@code command}, in order. */
    private List<int[]> repliesTo(int command) {
        int opcode = ~command & 0xFF;
        return rcx.replies().stream().filter(reply -> reply[0] == opcode).toList();
    }

    private int[] lastReplyTo(int command) {
        List<int[]> replies = repliesTo(command);
        assertThat(replies).isNotEmpty();
        return replies.get(replies.size() - 1);
    }

    @Test
    @DisplayName("A profile while a motion runs is busy")
    void shouldBeBusyForAProfileWhileRunning() {
        // Given
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, 100, 0, 200, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0x90, 0x01);
        rcx.receiveAfter(5, PROFILE, SESSION, 100, 0, 200, 0, 1, 10);

        // When
        run();

        // Then
        assertThat(lastReplyTo(PROFILE)).containsExactly(0x98, BUSY);
    }

    @Test
    @DisplayName("Arming while a motion runs is busy")
    void shouldBeBusyForArmingWhileRunning() {
        // Given
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, 100, 0, 200, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0x90, 0x01);
        rcx.receiveAfter(5, ARM, SESSION, 1, FULL_POWER_FLAGS);

        // When
        run();

        // Then
        assertThat(lastReplyTo(ARM)).containsExactly(0x9C, BUSY, 1, 1);
    }

    @Test
    @DisplayName("Arming for another mapping is an invalid argument")
    void shouldRefuseAnotherMapping() {
        // Given
        rcx.receive(ARM, SESSION, 2, FULL_POWER_FLAGS);

        // When
        run();

        // Then
        assertThat(lastReplyTo(ARM)).containsExactly(0x9C, INVALID_ARGUMENT, 1, 1);
    }

    @Test
    @DisplayName("A start for motion 0 is an invalid argument, since the host numbers motions from 1")
    void shouldRefuseMotionZero() {
        // Given
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(START, SESSION, 0, 0, 0, 0, 0);

        // When
        run();

        // Then
        assertThat(lastReplyTo(START)).containsExactly(0x89, INVALID_ARGUMENT, SESSION, 0);
    }

    @Test
    @DisplayName("A target a half ring away from the position is out of range")
    void shouldRefuseAnAmbiguousTarget() {
        // Given
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(START, SESSION, 1, 0x00, 0x80, 0, 0);

        // When
        run();

        // Then
        assertThat(lastReplyTo(START)).containsExactly(0x89, OUT_OF_RANGE, SESSION, 1);
    }

    @Test
    @DisplayName("An older motion than the last one started is stale")
    void shouldRefuseAnOlderMotion() {
        // Given
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(START, SESSION, 2, 0, 0, 0, 0);
        rcx.receive(START, SESSION, 1, 0, 0, 0, 0);

        // When
        run();

        // Then
        assertThat(lastReplyTo(START)).containsExactly(0x89, STALE_COMMAND, SESSION, 1);
    }

    @Test
    @DisplayName("A newer motion after a stall is refused as not armed, until the pair is armed again")
    void shouldRefuseANewMotionAfterAStall() {
        // Given
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.jam(LEFT);
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, 100, 0, 200, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 40, 0, 0, 0);
        rcx.receiveAfter(200, START, SESSION, 2, 40, 0, 0, 0);

        // When
        run();

        // Then
        assertThat(lastReplyTo(START)).containsExactly(0x89, NOT_ARMED, SESSION, 2);
    }

    @Test
    @DisplayName("A stop after the motion has finished is a duplicate and changes nothing")
    void shouldAnswerAStopAfterCompletionAsDuplicate() {
        // Given
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, 100, 0, 200, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 40, 0, 0, 0);
        rcx.receiveAfter(200, STOP, SESSION, 1);

        // When
        run();

        // Then
        assertThat(lastReplyTo(STOP)).containsExactly(0x9D, DUPLICATE, SESSION, 1);
    }

    @Test
    @DisplayName("A command for another session is an invalid session")
    void shouldRefuseAnotherSession() {
        // Given
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(STOP, 2, 1);

        // When
        run();

        // Then
        assertThat(lastReplyTo(STOP)).containsExactly(0x9D, INVALID_SESSION, 2, 1);
    }

    @Test
    @DisplayName("Status reports the motion the pair last started")
    void shouldReportTheMotionInStatus() {
        // Given
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(START, SESSION, 1, 0, 0, 0, 0);
        rcx.receive(STATUS, SESSION);

        // When
        run();

        // Then
        assertThat(lastReplyTo(STATUS)[1]).isEqualTo(1);
    }

    @Test
    @DisplayName("The brake bit in the configuration makes a stop brake the wheels rather than float them")
    void shouldBrakeOnStopWhenConfigured() {
        // Given
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS | 0x10);
        rcx.receive(PROFILE, SESSION, 100, 0, 200, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0x90, 0x01);
        rcx.receiveAfter(60, STOP, SESSION, 1);

        // When
        run();

        // Then
        assertThat(rcx.commandedMode(LEFT)).isEqualTo(MODE_BRAKE);
    }

    @Test
    @DisplayName("The maximum power in the configuration caps the power every motion is driven at")
    void shouldCapThePowerAtTheConfiguredMaximum() {
        // Given: maximum power 2 (configuration bits 5-7 = 2)
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.receive(ARM, SESSION, 1, 0x40);
        rcx.receive(PROFILE, SESSION, 100, 0, 200, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 40, 0, 0, 0);
        rcx.receiveAfter(200, STATUS, SESSION);

        // When
        run();

        // Then
        assertThat(rcx.highestPower(LEFT)).isBetween(1, 2);
        assertThat(lastReplyTo(STATUS)[3]).isEqualTo(3);
    }
}
