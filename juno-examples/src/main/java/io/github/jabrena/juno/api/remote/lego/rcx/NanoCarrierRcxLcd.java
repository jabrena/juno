package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Tests RCX LCD numbers, text, icons, and the motor status indicator on channel 1. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxLcd {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int DISPLAY_MILLIS = 3000;
    private static final int FINAL_ICON_MILLIS = 5000;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxLcd() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxLcd lcd = remote.getLcd();

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        waitForFirmware(lcd);

        Serial.println("RCX LCD test: channel 1");
        testNumbers(lcd);
        testText(lcd);
        Serial.println("Clear text before icon test");
        lcd.clear();
        Delay.millis(DISPLAY_MILLIS);
        testIcons(lcd);
        Serial.println("Holding all icons on display");
        lcd.showIcon(RcxLcd.ICON_ALL, true);
        Delay.millis(FINAL_ICON_MILLIS);
        lcd.clear();
        Serial.println("RCX LCD test finished");
    }

    private static void waitForFirmware(RcxLcd lcd) {
        while (!lcd.showNumber(0)) {
            Serial.println("No Juno firmware response; check channel 1 and IR alignment");
            Delay.millis(RETRY_MILLIS);
        }
    }

    private static void testNumbers(RcxLcd lcd) {
        showNumber(lcd, 1234);
        showNumber(lcd, -42);
        showNumber(lcd, 0);
    }

    private static void testText(RcxLcd lcd) {
        showText(lcd, "JUNO");
        showText(lcd, "TEST");
        showText(lcd, "VIEW");
    }

    private static void testIcons(RcxLcd lcd) {
        flashIcon(lcd, "standing", RcxLcd.ICON_STANDING);
        flashIcon(lcd, "walking", RcxLcd.ICON_WALKING);
        flashIcon(lcd, "sensor 1 view", RcxLcd.ICON_SENSOR_1_VIEW);
        flashIcon(lcd, "sensor 1 pressed", RcxLcd.ICON_SENSOR_1_PRESSED);
        flashIcon(lcd, "motor A view", RcxLcd.ICON_MOTOR_A_VIEW);
        flashIcon(lcd, "battery low", RcxLcd.ICON_BATTERY_LOW);
        flashIcon(lcd, "range short", RcxLcd.ICON_RANGE_SHORT);
        flashIcon(lcd, "range long", RcxLcd.ICON_RANGE_LONG);
        flashIcon(lcd, "all segments", RcxLcd.ICON_ALL);
    }

    private static void showNumber(RcxLcd lcd, int value) {
        Serial.print("Number ");
        Serial.print(value);
        Serial.println(lcd.showNumber(value) ? ": acknowledged" : ": no acknowledgement");
        Delay.millis(DISPLAY_MILLIS);
    }

    private static void showText(RcxLcd lcd, String text) {
        Serial.print("Text ");
        Serial.print(text);
        Serial.println(lcd.showText(text) ? ": acknowledged" : ": no acknowledgement");
        Delay.millis(DISPLAY_MILLIS);
    }

    private static void flashIcon(RcxLcd lcd, String name, int icon) {
        Serial.print("Icon ");
        Serial.print(name);
        Serial.println(lcd.showIcon(icon, true) ? ": acknowledged" : ": no acknowledgement");
        Delay.millis(DISPLAY_MILLIS);
        lcd.showIcon(icon, false);
    }
}
