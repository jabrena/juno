package io.github.jabrena.juno.api.hardware.device.lidar;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.lidar.RplidarA2;
import io.github.jabrena.juno.hardware.device.usb.Cp210xSerial;
import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads a RPLIDAR A2 (A2M8) through the Arduino USB Host Shield on an UNO R4 WiFi: the lidar's USB adapter (a CP2102
 * bridge) plugs into the shield's USB-A socket, and the whole USB stack, from the MAX3421E registers to the lidar
 * protocol, is plain Java over SPI. Every tenth revolution prints its point count, the distances at 0, 90, 180 and 270
 * degrees and the nearest obstacle; every twentieth also prints all 360 distances, in millimetres (0 means no return).
 * The lidar can be unplugged and plugged in again while the program runs: it notices
 * the adapter is gone, or that revolutions stopped arriving, and connects again.
 *
 * <p>Stack the shield on the board (SPI through the ICSP header, chip select D10) and plug the adapter in.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.lidar.UsbHostRplidarA2Scan -Djuno.board=arduino-uno-r4-wifi
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR4WiFiBoard.class})
public final class UsbHostRplidarA2Scan {
    private static final int RETRY_MILLIS = 2000;
    private static final int SPIN_UP_MILLIS = 2000;
    private static final int SCAN_ATTEMPTS = 3;
    /** A revolution takes 100 to 200 ms: this long without one means the lidar stopped or rebooted. */
    private static final int STALL_MILLIS = 3000;
    private static final int SUMMARY_EVERY = 10;
    private static final int TABLE_EVERY = 20;
    private static final int TABLE_COLUMNS = 10;

    private UsbHostRplidarA2Scan() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1500);
        Serial.println("USB Host Shield + RPLIDAR A2");

        UsbHostShield usb = new UsbHostShield();
        Cp210xSerial bridge = new Cp210xSerial(usb);
        RplidarA2 lidar = new RplidarA2(bridge);
        Clock clock = MCU.getClock();
        int[] info = new int[RplidarA2.INFO_LENGTH];
        int[] scan = new int[RplidarA2.DEGREES];

        while (!usb.begin()) {
            Serial.print("MAX3421E not found, revision ");
            Serial.println(usb.revision());
            Delay.millis(RETRY_MILLIS);
        }
        while (true) {
            if (connect(usb, bridge, lidar, info)) {
                scanUntilLost(lidar, clock, scan);
                Serial.print("lidar lost after ");
                Serial.print(lidar.revolutions());
                Serial.print(" revolutions and ");
                Serial.print(lidar.nodes());
                Serial.print(" nodes: ");
                if (lidar.isConnected()) {
                    Serial.println("no revolution for 3 s, reconnecting");
                } else {
                    Serial.print("USB error ");
                    Serial.print(bridge.lastError());
                    Serial.println(", reconnecting");
                }
            }
            Delay.millis(RETRY_MILLIS);
        }
    }

    /**
     * Enumerates the adapter, starts the motor and the scan. Returns {@code false} when any step fails; the caller
     * starts over, which also recovers a lidar that was plugged in while still booting.
     */
    private static boolean connect(UsbHostShield usb, Cp210xSerial bridge, RplidarA2 lidar, int[] info) {
        if (!lidar.begin()) {
            Serial.print("adapter not ready: connected ");
            Serial.print(usb.isDeviceConnected());
            Serial.print(", error ");
            Serial.println(bridge.lastError());
            return false;
        }
        lidar.startMotor();
        Delay.millis(SPIN_UP_MILLIS);
        if (lidar.info(info)) {
            Serial.print("model ");
            Serial.print(info[0]);
            Serial.print(", firmware ");
            Serial.print(info[1]);
            Serial.print(".");
            Serial.print(info[2]);
            Serial.print(", hardware ");
            Serial.print(info[3]);
        } else {
            Serial.print("no answer to GET_INFO");
        }
        Serial.print(", health ");
        Serial.println(lidar.health());
        for (int attempt = 0; attempt < SCAN_ATTEMPTS; attempt++) {
            if (lidar.startScan()) {
                Serial.println("scanning");
                return true;
            }
        }
        Serial.print("scan not acknowledged, resetting the lidar; error ");
        Serial.println(bridge.lastError());
        lidar.reset();
        return false;
    }

    /** Polls until the adapter is lost or no revolution completes for {@link #STALL_MILLIS}. */
    private static void scanUntilLost(RplidarA2 lidar, Clock clock, int[] scan) {
        int lastRevolution = clock.millis();
        while (lidar.isConnected() && clock.millis() - lastRevolution < STALL_MILLIS) {
            if (lidar.poll(scan)) {
                lastRevolution = clock.millis();
                int revolution = lidar.revolutions();
                if (revolution % SUMMARY_EVERY == 0) {
                    summary(revolution, scan);
                }
                if (revolution % TABLE_EVERY == 0) {
                    table(scan);
                }
            }
        }
    }

    private static void summary(int revolution, int[] scan) {
        int points = 0;
        int nearest = 0;
        int nearestDegree = 0;
        for (int degree = 0; degree < RplidarA2.DEGREES; degree++) {
            int distance = scan[degree];
            if (distance > 0) {
                points++;
                if (nearest == 0 || distance < nearest) {
                    nearest = distance;
                    nearestDegree = degree;
                }
            }
        }
        Serial.print("rev ");
        Serial.print(revolution);
        Serial.print(": points ");
        Serial.print(points);
        Serial.print(", 0deg ");
        Serial.print(scan[0]);
        Serial.print(", 90deg ");
        Serial.print(scan[90]);
        Serial.print(", 180deg ");
        Serial.print(scan[180]);
        Serial.print(", 270deg ");
        Serial.print(scan[270]);
        Serial.print(", nearest ");
        Serial.print(nearest);
        Serial.print(" mm at ");
        Serial.println(nearestDegree);
    }

    /** All 360 distances, ten degrees per row, each row headed by its first degree. */
    private static void table(int[] scan) {
        for (int row = 0; row < RplidarA2.DEGREES; row += TABLE_COLUMNS) {
            Serial.print(row);
            Serial.print(":");
            for (int column = 0; column < TABLE_COLUMNS; column++) {
                Serial.print(" ");
                Serial.print(scan[row + column]);
            }
            Serial.println("");
        }
    }
}
