package io.github.jabrena.juno.api.hardware.device.ti;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Enumerates a TI graphing calculator plugged into the USB Host Shield with its ordinary computer cable (the calculator
 * is the USB device, as with TI Connect) and prints its vendor and product IDs and its configuration descriptor, every
 * five seconds, so the endpoints a link-protocol driver needs can be read off the serial log.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.ti.UsbHostTiCalculatorProbe -Djuno.board=arduino-uno-r4-wifi
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR4WiFiBoard.class})
public final class UsbHostTiCalculatorProbe {
    private static final int PERIOD_MILLIS = 5000;
    private static final int CONFIGURATION_LENGTH = 64;

    private UsbHostTiCalculatorProbe() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1500);
        UsbHostShield usb = new UsbHostShield();
        byte[] configuration = new byte[CONFIGURATION_LENGTH];
        while (true) {
            if (!usb.begin()) {
                Serial.print("MAX3421E not found, revision ");
                Serial.println(usb.revision());
            } else if (!usb.attach()) {
                Serial.println("no device enumerated (connected: " + usb.isDeviceConnected() + ")");
            } else {
                Serial.print("device ");
                Serial.print(usb.vendorId());
                Serial.print(":");
                Serial.print(usb.productId());
                Serial.print(usb.isLowSpeed() ? " low speed" : " full speed");
                Serial.print(", EP0 packet ");
                Serial.println(usb.maxPacketSize0());
                int length = usb.configurationDescriptor(configuration, CONFIGURATION_LENGTH);
                Serial.print("configuration (");
                Serial.print(length);
                Serial.print(" bytes):");
                for (int i = 0; i < length; i++) {
                    Serial.print(" ");
                    Serial.print(configuration[i] & 0xFF);
                }
                Serial.println("");
            }
            Delay.millis(PERIOD_MILLIS);
        }
    }
}
