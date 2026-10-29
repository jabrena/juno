package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxButtons;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Monitors the RCX Run, View, and Prgm buttons on channel 1 through the Nano Connector Carrier.
 *
 * <p>Prgm advances the brick's firmware channel. Polling therefore pauses while the brick is on another channel and
 * resumes when Prgm returns it to channel 1.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxButtons {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int POLL_MILLIS = 250;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxButtons() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxButtons buttons = remote.getButtons();
        RcxLcd lcd = remote.getLcd();

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        waitForBrick(remote);
        lcd.clear();
        buttons.clicked();
        Serial.println("RCX buttons: channel 1; press Run, View, or Prgm");

        while (true) {
            int clicked = buttons.clicked();
            if (clicked < 0) {
                Serial.println("No RCX response");
            } else {
                reportClicks(clicked, lcd);
            }
            Delay.millis(POLL_MILLIS);
        }
    }

    private static void waitForBrick(RemoteRcx remote) {
        int millivolts = remote.getBattery().millivolts();
        while (millivolts < 0) {
            Serial.println("No RCX response; check channel 1 and IR alignment");
            Delay.millis(RETRY_MILLIS);
            millivolts = remote.getBattery().millivolts();
        }
        Serial.print("RCX battery: ");
        Serial.print(millivolts);
        Serial.println(" mV");
    }

    private static void reportClicks(int clicked, RcxLcd lcd) {
        if (clicked == 0) {
            return;
        }
        if ((clicked & RcxButtons.RUN) != 0) {
            report("RUN", lcd);
        }
        if ((clicked & RcxButtons.VIEW) != 0) {
            report("VIEW", lcd);
        }
        if ((clicked & RcxButtons.PRGM) != 0) {
            report("PRGM", lcd);
        }
    }

    private static void report(String button, RcxLcd lcd) {
        Serial.print("Button ");
        Serial.println(button);
        lcd.showText(button);
    }
}
