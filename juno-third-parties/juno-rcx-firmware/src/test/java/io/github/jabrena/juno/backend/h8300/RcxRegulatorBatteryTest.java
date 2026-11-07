package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The battery in the regulated protocol: the same reply as the classical command, also while a motion runs. */
@DisplayName("RCX regulated pair battery")
class RcxRegulatorBatteryTest {
    private static final int CLASSICAL_BATTERY = 0x30;
    private static final int REGULATED_BATTERY = 0x70;
    private static final int ARM = 0x63;
    private static final int STATUS = 0x61;
    private static final int PROFILE = 0x67;
    private static final int START = 0x76;
    private static final int SESSION = 1;
    private static final int FULL_POWER_FLAGS = 0xE0;
    private static final int STEPS = 3_000_000;

    private static int[] replyTo(FakeRcx rcx, int command) {
        int opcode = ~command & 0xFF;
        List<int[]> replies = rcx.replies().stream().filter(reply -> reply[0] == opcode).toList();
        assertThat(replies).isNotEmpty();
        return replies.get(replies.size() - 1);
    }

    @Test
    @DisplayName("The regulated battery command replies with the same bytes as the classical battery command")
    void shouldReplyWithTheClassicalBatteryBytes() {
        // Given
        FakeRcx rcx = new FakeRcx();
        rcx.batteryReading(300);
        rcx.receive(CLASSICAL_BATTERY);
        rcx.receive(REGULATED_BATTERY);

        // When
        new H8Simulator(new RcxRemoteFirmware().image(), rcx).runFor(STEPS);

        // Then: the complement of each opcode, then the same two bytes of millivolts
        int[] classical = replyTo(rcx, CLASSICAL_BATTERY);
        int[] regulated = replyTo(rcx, REGULATED_BATTERY);
        assertThat(classical).hasSize(3);
        assertThat(regulated).hasSize(3);
        assertThat(regulated[1]).isEqualTo(classical[1]);
        assertThat(regulated[2]).isEqualTo(classical[2]);
    }

    @Test
    @DisplayName("A battery read while a motion runs does not disturb the motion")
    void shouldReadTheBatteryWhileAMotionRuns() {
        // Given
        FakeRcx rcx = new FakeRcx();
        rcx.continuousAnalog();
        rcx.wheelPlant();
        rcx.batteryReading(300);
        rcx.receive(ARM, SESSION, 1, FULL_POWER_FLAGS);
        rcx.receive(PROFILE, SESSION, 100, 0, 40, 0, 1, 10);
        rcx.receive(START, SESSION, 1, 0x90, 0x01, 0x70, 0xFE);
        rcx.receiveAfter(5, REGULATED_BATTERY);
        rcx.receiveAfter(2000, STATUS, SESSION);

        // When
        new H8Simulator(new RcxRemoteFirmware().image(), rcx).runFor(STEPS);

        // Then
        assertThat(replyTo(rcx, REGULATED_BATTERY)).hasSize(3);
        int[] status = replyTo(rcx, STATUS);
        assertThat(status[3]).isEqualTo(3);
    }
}
