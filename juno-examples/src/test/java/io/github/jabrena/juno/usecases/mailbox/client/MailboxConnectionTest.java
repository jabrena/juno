package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailboxConnectionTest {
    private final Wifi wifi = mock(Wifi.class);
    private final Clock clock = mock(Clock.class);

    @Test
    void connectsOnceTheJoinedLinkHasAnAddress() {
        // Given
        MailboxConnection connection = new MailboxConnection(wifi, clock, true);
        when(wifi.status()).thenReturn(Wifi.STATUS_CONNECTED);
        address(0, 0, 0, 0);

        // When DHCP has not answered yet
        connection.step();

        // Then
        assertThat(connection.isConnected()).isFalse();

        // When the address arrives at the next poll
        address(192, 168, 1, 52);
        when(clock.millis()).thenReturn(500);
        connection.step();

        // Then
        assertThat(connection.isConnected()).isTrue();
        assertThat(connection.address(3)).isEqualTo(52);
    }

    @Test
    void reportsASlowJoinWithoutBlocking() {
        // Given
        MailboxConnection connection = new MailboxConnection(wifi, clock, true);
        when(wifi.status()).thenReturn(0);
        connection.step();
        int revision = connection.revision();

        // When
        when(clock.millis()).thenReturn(30000);
        connection.step();

        // Then
        assertThat(connection.state()).isEqualTo(MailboxConnection.SLOW);
        assertThat(connection.revision()).isGreaterThan(revision);
    }

    @Test
    void pollsAtMostEveryHalfSecond() {
        // Given
        MailboxConnection connection = new MailboxConnection(wifi, clock, true);
        connection.step();

        // When
        when(clock.millis()).thenReturn(499);
        connection.step();

        // Then
        verify(wifi, times(1)).status();
    }

    @Test
    void neverPollsWithoutWifiSettings() {
        // Given
        MailboxConnection connection = new MailboxConnection(wifi, clock, false);

        // When
        connection.step();

        // Then
        assertThat(connection.state()).isEqualTo(MailboxConnection.NO_SETTINGS);
        verify(wifi, never()).status();
    }

    private void address(int a, int b, int c, int d) {
        doAnswer(invocation -> {
            int[] octets = invocation.getArgument(0);
            octets[0] = a;
            octets[1] = b;
            octets[2] = c;
            octets[3] = d;
            return null;
        }).when(wifi).localIP(any());
    }
}
