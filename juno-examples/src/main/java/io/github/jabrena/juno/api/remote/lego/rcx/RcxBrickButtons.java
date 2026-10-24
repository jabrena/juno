package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxButtons;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Watches the RCX brick's own View, Prgm and Run buttons. It starts by blanking the brick's display, then shows the
 * name of each clicked button there ({@code VIEW}, {@code PRGM}, {@code RUN}, as close as the seven-segment digits
 * get) and prints it over Serial. Needs Juno's own RCX firmware ({@code RcxRemoteFirmware}).
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with VCC on 5V
 * and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
@SuppressWarnings("java:S2189")
public class RcxBrickButtons {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int POLL_MILLIS = 200;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    private final RcxLcd lcd;

    RcxBrickButtons(RcxLcd lcd) {
        this.lcd = lcd;
    }

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        RcxButtons buttons = remote.getButtons();
        RcxBrickButtons app = new RcxBrickButtons(remote.getLcd());
        while (!app.lcd.clear()) {
            Serial.println("The RCX did not answer: is it on, running Juno's firmware, in range?");
            Delay.millis(RETRY_MILLIS);
        }
        buttons.clicked();
        Serial.println("Click View, Prgm or Run on the RCX");

        while (true) {
            int clicked = buttons.clicked();
            if (clicked > 0) {
                if ((clicked & RcxButtons.VIEW) != 0) {
                    app.show("VIEW");
                }
                if ((clicked & RcxButtons.PRGM) != 0) {
                    app.show("PRGM");
                }
                if ((clicked & RcxButtons.RUN) != 0) {
                    app.show("RUN");
                }
            }
            Delay.millis(POLL_MILLIS);
        }
    }

    /** Shows {@code name} on the brick and prints it. */
    private void show(String name) {
        Serial.println(name);
        lcd.showText(name);
    }
}
