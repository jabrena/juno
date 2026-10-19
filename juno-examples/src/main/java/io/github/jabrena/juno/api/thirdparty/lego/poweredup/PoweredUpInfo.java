package io.github.jabrena.juno.api.thirdparty.lego.poweredup;

import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;

import static io.github.jabrena.juno.api.thirdparty.lego.poweredup.PoweredUpState.CONTENT_Y;
import static io.github.jabrena.juno.api.thirdparty.lego.poweredup.PoweredUpState.MARGIN;

/** The INFO view of {@link PoweredUpHubTFT}. */
final class PoweredUpInfo {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
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
        TftTouchShield.print(HUB.batteryPercent());
        TftTouchShield.print(" %    ");
        TftTouchShield.setCursor(MARGIN, infoLine(2));
        TftTouchShield.print("RSSI ");
        TftTouchShield.print(HUB.rssi());
        TftTouchShield.print(" dB    ");
        TftTouchShield.setCursor(MARGIN, infoLine(3));
        TftTouchShield.print("Firmware ");
        printVersion(HUB.firmwareVersion());
        TftTouchShield.setCursor(MARGIN, infoLine(4));
        TftTouchShield.print("Hardware ");
        printVersion(HUB.hardwareVersion());
        drawTilt(s);
    }

    private static int infoLine(int index) {
        return CONTENT_Y + 8 + index * 26;
    }

    private static void printVersion(int version) {
        TftTouchShield.print(HUB.versionMajor(version));
        TftTouchShield.print(".");
        TftTouchShield.print(HUB.versionMinor(version));
        TftTouchShield.print(".");
        TftTouchShield.print(HUB.versionBugfix(version));
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
        TftTouchShield.print(HUB.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 0, 2));
        TftTouchShield.print(" ");
        TftTouchShield.print(HUB.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 1, 2));
        TftTouchShield.print("    ");
    }
}
