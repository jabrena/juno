package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;

import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.CONTENT_Y;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.MARGIN;

/** The INFO view of {@link PoweredUpHubTFT}. */
final class PoweredUpInfo {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private PoweredUpInfo() {
    }

    /** The values read from the hub, redrawn in place (the background color overwrites the old text). */
    static void draw(TftTouchShield tft, PoweredUpState s) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, infoLine(0));
        printHubType(tft, s);
        tft.setCursor(MARGIN, infoLine(1));
        tft.print("Battery ");
        tft.print(HUB.batteryPercent());
        tft.print(" %    ");
        tft.setCursor(MARGIN, infoLine(2));
        tft.print("RSSI ");
        tft.print(HUB.rssi());
        tft.print(" dB    ");
        tft.setCursor(MARGIN, infoLine(3));
        tft.print("Firmware ");
        printVersion(tft, HUB.firmwareVersion());
        tft.setCursor(MARGIN, infoLine(4));
        tft.print("Hardware ");
        printVersion(tft, HUB.hardwareVersion());
        drawTilt(tft, s);
    }

    private static int infoLine(int index) {
        return CONTENT_Y + 8 + index * 26;
    }

    private static void printVersion(TftTouchShield tft, int version) {
        tft.print(HUB.versionMajor(version));
        tft.print(".");
        tft.print(HUB.versionMinor(version));
        tft.print(".");
        tft.print(HUB.versionBugfix(version));
        tft.print("   ");
    }

    private static void printHubType(TftTouchShield tft, PoweredUpState s) {
        if (s.hubType == PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            tft.print("Technic Hub      ");
        } else if (s.hubType == PoweredUpHubRemote.TYPE_MOVE_HUB) {
            tft.print("Move Hub         ");
        } else if (s.hubType == PoweredUpHubRemote.TYPE_CITY_HUB) {
            tft.print("City Hub         ");
        } else {
            tft.print("Hub 0x");
            tft.print(s.hubType);
            tft.print("        ");
        }
    }

    /** Tilt angles (degrees) from the Technic Hub's built-in sensor; other hubs have none. */
    private static void drawTilt(TftTouchShield tft, PoweredUpState s) {
        tft.setCursor(MARGIN, infoLine(5));
        if (s.hubType != PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            tft.print("Tilt n/a            ");
            return;
        }
        tft.print("Tilt ");
        tft.print(HUB.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 0, 2));
        tft.print(" ");
        tft.print(HUB.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 1, 2));
        tft.print("    ");
    }
}
