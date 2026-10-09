package io.github.jabrena.juno.api.lego;

import static io.github.jabrena.juno.api.lego.PoweredUpState.*;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The INFO view of {@link PoweredUpHubTFT}. */
final class PoweredUpInfo {
    private PoweredUpInfo() {
    }

    /** The values read from the hub, redrawn in place (the background color overwrites the old text). */
    static void draw(PoweredUpState s) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, infoLine(0));
        printHubType(s);
        TftTouchShield.setCursor(MARGIN, infoLine(1));
        TftTouchShield.print("Battery ");
        TftTouchShield.print(PoweredUpHubRemote.batteryPercent());
        TftTouchShield.print(" %    ");
        TftTouchShield.setCursor(MARGIN, infoLine(2));
        TftTouchShield.print("RSSI ");
        TftTouchShield.print(PoweredUpHubRemote.rssi());
        TftTouchShield.print(" dB    ");
        TftTouchShield.setCursor(MARGIN, infoLine(3));
        TftTouchShield.print("Firmware ");
        printVersion(PoweredUpHubRemote.firmwareVersion());
        TftTouchShield.setCursor(MARGIN, infoLine(4));
        TftTouchShield.print("Hardware ");
        printVersion(PoweredUpHubRemote.hardwareVersion());
        drawTilt(s);
    }

    private static int infoLine(int index) {
        return CONTENT_Y + 8 + index * 26;
    }

    private static void printVersion(int version) {
        TftTouchShield.print(PoweredUpHubRemote.versionMajor(version));
        TftTouchShield.print(".");
        TftTouchShield.print(PoweredUpHubRemote.versionMinor(version));
        TftTouchShield.print(".");
        TftTouchShield.print(PoweredUpHubRemote.versionBugfix(version));
        TftTouchShield.print("   ");
    }

    private static void printHubType(PoweredUpState s) {
        if (s.hubType == PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            TftTouchShield.print("Technic Hub      ");
        } else if (s.hubType == PoweredUpHubRemote.TYPE_MOVE_HUB) {
            TftTouchShield.print("Move Hub         ");
        } else if (s.hubType == PoweredUpHubRemote.TYPE_CITY_HUB) {
            TftTouchShield.print("City Hub         ");
        } else {
            TftTouchShield.print("Hub 0x");
            TftTouchShield.print(s.hubType);
            TftTouchShield.print("        ");
        }
    }

    /** Tilt angles (degrees) from the Technic Hub's built-in sensor; other hubs have none. */
    private static void drawTilt(PoweredUpState s) {
        TftTouchShield.setCursor(MARGIN, infoLine(5));
        if (s.hubType != PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            TftTouchShield.print("Tilt n/a            ");
            return;
        }
        TftTouchShield.print("Tilt ");
        TftTouchShield.print(PoweredUpHubRemote.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 0, 2));
        TftTouchShield.print(" ");
        TftTouchShield.print(PoweredUpHubRemote.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 1, 2));
        TftTouchShield.print("    ");
    }
}
