package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryViewTest {
    private final Navigator navigator = mock(Navigator.class);
    private final Display display = mock(Display.class);
    private final Wifi wifi = mock(Wifi.class);
    private final MailboxApi api = mock(MailboxApi.class);
    private final MailboxConnection connection = new MailboxConnection(wifi, mock(Clock.class), true);
    private final Footer footer = new Footer(display);
    private final SettingsView settings =
            new SettingsView(navigator, display, mock(Keyboard.class), api, connection, footer);
    private final DiscoveryView view = new DiscoveryView(navigator, display, connection, api, settings, footer);

    @Test
    void probesTheAddressesInOrderSkippingItsOwnAndOpensTheInboxOnAMatch() {
        // Given the Cardputer is 192.168.1.2 and the mailbox answers at .4
        connect(192, 168, 1, 2);
        when(api.probe("192.168.1.4")).thenReturn(true);
        view.enter();

        // When
        for (int frame = 0; frame < 3; frame++) {
            view.render();
        }

        // Then .1, .3 and .4 were asked in order, and .4 became the host
        ArgumentCaptor<String> probed = ArgumentCaptor.forClass(String.class);
        verify(api, atLeastOnce()).probe(probed.capture());
        assertThat(probed.getAllValues()).containsExactly("192.168.1.1", "192.168.1.3", "192.168.1.4");
        verify(api).host("192.168.1.4");
        verify(navigator).show(1);
    }

    @Test
    void fallsBackToTheManualSettingsWhenNoAddressAnswers() {
        // Given
        connect(10, 0, 0, 1);
        view.enter();

        // When every address but its own, .2 to .254, is asked, then once more
        for (int frame = 0; frame < 254; frame++) {
            view.render();
        }
        settings.enter();

        // Then
        verify(navigator).show(0);
        verify(display).print("Mailbox not found; enter its IP");
        verify(api, never()).host(anyString());
    }

    @Test
    void waitsForWifiBeforeProbing() {
        // Given the join has not finished
        view.enter();

        // When
        view.render();

        // Then
        verify(display).print("Waiting for WiFi");
        verify(api, never()).probe(anyString());
    }

    private void connect(int a, int b, int c, int d) {
        when(wifi.status()).thenReturn(Wifi.STATUS_CONNECTED);
        doAnswer(invocation -> {
            int[] octets = invocation.getArgument(0);
            octets[0] = a;
            octets[1] = b;
            octets[2] = c;
            octets[3] = d;
            return null;
        }).when(wifi).localIP(any());
        connection.step();
    }
}
