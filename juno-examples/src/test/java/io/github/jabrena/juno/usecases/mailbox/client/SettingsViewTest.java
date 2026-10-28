package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.Navigator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsViewTest {
    private final EmulatedSdCard card = new EmulatedSdCard();
    private final Navigator navigator = mock(Navigator.class);
    private final Display display = mock(Display.class);
    private final Keyboard keyboard = mock(Keyboard.class);
    private final MailboxApi client = mock(MailboxApi.class);
    private final MailboxConnection connection = new MailboxConnection(mock(Wifi.class), mock(Clock.class), true);
    private final ProgressView progress = new ProgressView(
            navigator, display, client, new MailboxModel(), new Footer(display), mock(Clock.class));
    private final SettingsView view = new SettingsView(navigator, display, keyboard, client, connection,
            new MailboxKey(card), new MailboxPolling(card), progress, new Footer(display));

    @Test
    void savingConfigurationEntersTheRefreshTransition() {
        card.insert();
        when(keyboard.isControlPressed()).thenReturn(false);
        view.enter();
        view.key(Keyboard.TAB, false);
        for (char character : "192.168.1.47".toCharArray()) {
            view.key(character, false);
        }

        view.key(Keyboard.TAB, false);
        view.key('1', false);
        view.key(Keyboard.ENTER, false);

        verify(client).host("192.168.1.47");
        verify(client).beginRefresh();
        verify(navigator).show(6);
    }
}
