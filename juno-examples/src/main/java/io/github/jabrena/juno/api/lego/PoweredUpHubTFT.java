package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * A touch screen remote for a LEGO Powered Up hub (Technic Hub, Move Hub or City Hub), drawn on the
 * ELEGOO 2.8" TFT touch shield. The button along the bottom connects to / disconnects from the hub
 * (switch the hub on first; connecting blocks the screen for up to {@code CONNECT_MILLIS} while the
 * Arduino scans). Four tabs along the top choose the view:
 * <ul>
 *   <li><b>INFO</b>: hub type, battery level, signal strength (RSSI), firmware and hardware versions,
 *       and on a Technic Hub its tilt angles;</li>
 *   <li><b>LED</b>: a grid of nine colors, a touch sets the hub LED;</li>
 *   <li><b>MOTORS</b>: all four ports A..D, each with the device the hub reports on it (its automatic
 *       detection of what is plugged in: a motor type, a sensor or nothing). A port holding a motor has
 *       a {@code -} and a {@code +} button that change its power in steps of {@code POWER_STEP} percent
 *       and shows the tacho (position) value under it; a port with a sensor shows the sensor's name; an
 *       empty port stays blank. Below are a STOP button that brakes every motor and a ZERO button that
 *       sets the tacho values to 0 (an offset kept on the Arduino; the hub's own position is not
 *       changed).</li>
 *   <li><b>PAIR</b>: the first two motors the hub detected, driven as a pair. SYNC links them into one
 *       virtual port, so a single {@code -} / {@code +} sets both at once with one command; with SYNC off
 *       each motor has its own. Three stop levels can be chosen for the STOP button: COAST (power 0, the
 *       motors run free), BRAKE (they are slowed actively) and HOLD (they resist being turned). ZERO sets
 *       the two motors' tacho values to 0.</li>
 * </ul>
 * Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubTFT {
    private static final int PORTS = 4;
    private static final int POWER_STEP = 20;
    private static final int CONNECT_MILLIS = 10000;
    private static final int TOUCH_LOCKOUT_MILLIS = 250;
    private static final int REFRESH_MILLIS = 500;

    private static final int VIEW_INFO = 0;
    private static final int VIEW_LED = 1;
    private static final int VIEW_MOTORS = 2;
    private static final int VIEW_PAIR = 3;

    private static final int STOP_COAST = 0;
    private static final int STOP_BRAKE = 1;
    private static final int STOP_HOLD = 2;

    private static final int WIDTH = 240;
    private static final int MARGIN = 5;
    private static final int HEADER_HEIGHT = 24;
    private static final int TAB_Y = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_WIDTH = 60;
    private static final int CONTENT_Y = 62;
    private static final int CONTENT_HEIGHT = 218;
    private static final int BOTTOM_Y = 284;
    private static final int BOTTOM_HEIGHT = 32;

    private static final int SMALL_BUTTON_WIDTH = 50;
    private static final int WIDE_BUTTON_WIDTH = 110;
    private static final int ROW_FIRST = 66;
    private static final int ROW_PITCH = 40;
    private static final int ROW_STOP = 232;
    private static final int BUTTON_HEIGHT_MOTOR = 34;
    private static final int PAIR_ROW_SYNC = 66;
    private static final int PAIR_ROW_A = 106;
    private static final int PAIR_ROW_B = 146;
    private static final int ROW_LEVELS = 190;
    private static final int LEVEL_WIDTH = 72;
    private static final int LEVEL_GAP = 4;

    private static final int SWATCH_WIDTH = 70;
    private static final int SWATCH_HEIGHT = 60;
    private static final int SWATCH_GAP = 10;
    private static final int SWATCH_TOP = 70;

    private static final int[] LED_COLORS = {
        PoweredUpHubRemote.COLOR_RED,
        PoweredUpHubRemote.COLOR_ORANGE,
        PoweredUpHubRemote.COLOR_YELLOW,
        PoweredUpHubRemote.COLOR_GREEN,
        PoweredUpHubRemote.COLOR_CYAN,
        PoweredUpHubRemote.COLOR_BLUE,
        PoweredUpHubRemote.COLOR_PURPLE,
        PoweredUpHubRemote.COLOR_PINK,
        PoweredUpHubRemote.COLOR_WHITE
    };

    private int view;
    private int ledIndex;
    private final int[] power = new int[PORTS];
    private final int[] zero = new int[PORTS];
    private final int[] device = new int[PORTS];
    private int hubType;
    /** The two ports of the pair (the first two motors found), or -1 while there are fewer than two. */
    private int pairA = -1;
    private int pairB = -1;
    private boolean pairSync;
    /** The virtual port of the linked pair, or -1 when not linked. */
    private int link = -1;
    private int stopLevel = STOP_BRAKE;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        PoweredUpHubTFT app = new PoweredUpHubTFT();
        app.drawScreen();

        int lastTouch = Clock.millis();
        int lastRefresh = Clock.millis();
        while (true) {
            if (TftTouchShield.readTouch() && Clock.millis() - lastTouch > TOUCH_LOCKOUT_MILLIS) {
                lastTouch = Clock.millis();
                app.onTouch(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            if (Clock.millis() - lastRefresh > REFRESH_MILLIS) {
                lastRefresh = Clock.millis();
                app.checkLink();
                app.refresh();
            }
        }
    }

    // ---- touch handling -------------------------------------------------------------------

    private void onTouch(int x, int y) {
        if (inBox(x, y, MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT)) {
            toggleConnection();
        } else if (y >= TAB_Y && y < TAB_Y + TAB_HEIGHT) {
            selectView(x / TAB_WIDTH);
        } else if (PoweredUpHubRemote.isConnected()) {
            if (view == VIEW_LED) {
                onColorTouch(x, y);
            } else if (view == VIEW_MOTORS) {
                onMotorTouch(x, y);
            } else if (view == VIEW_PAIR) {
                onPairTouch(x, y);
            }
        }
    }

    private void selectView(int tab) {
        if (tab < 0 || tab > VIEW_PAIR || tab == view) {
            return;
        }
        view = tab;
        drawTabs();
        drawContent();
    }

    private void onColorTouch(int x, int y) {
        for (int i = 0; i < LED_COLORS.length; i++) {
            if (inBox(x, y, swatchX(i), swatchY(i), SWATCH_WIDTH, SWATCH_HEIGHT)) {
                ledIndex = i;
                PoweredUpHubRemote.setLedColor(LED_COLORS[i]);
                drawColors();
            }
        }
    }

    private void onMotorTouch(int x, int y) {
        if (inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            for (int port = 0; port < PORTS; port++) {
                zero[port] = PoweredUpHubRemote.readSensor(port);
            }
        } else if (inBox(x, y, MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            for (int port = 0; port < PORTS; port++) {
                if (isMotor(device[port])) {
                    power[port] = 0;
                    PoweredUpHubRemote.brakeMotor(port);
                }
            }
        } else {
            for (int port = 0; port < PORTS; port++) {
                int delta = isMotor(device[port]) ? motorDelta(x, y, rowOf(port)) : 0;
                if (delta != 0) {
                    power[port] = clamp(power[port] + delta);
                    PoweredUpHubRemote.setMotorPower(port, power[port]);
                }
            }
        }
        drawMotorRows();
    }

    private static int rowOf(int port) {
        return ROW_FIRST + port * ROW_PITCH;
    }

    private static boolean isMotor(int type) {
        return type == PoweredUpHubRemote.DEVICE_MEDIUM_LINEAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_MOVE_HUB_MEDIUM_LINEAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_MEDIUM_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_LARGE_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_XL_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR;
    }

    /** {@code -POWER_STEP}, {@code +POWER_STEP} or 0 depending on which button of the row was hit. */
    private int motorDelta(int x, int y, int row) {
        if (inBox(x, y, MARGIN, row, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            return -POWER_STEP;
        }
        if (inBox(x, y, WIDTH - MARGIN - SMALL_BUTTON_WIDTH, row, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            return POWER_STEP;
        }
        return 0;
    }

    // ---- PAIR view logic -------------------------------------------------------------------

    private void onPairTouch(int x, int y) {
        if (pairB < 0) {
            return;
        }
        if (inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, PAIR_ROW_SYNC, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            toggleSync();
        } else if (inBox(x, y, MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            stopPair();
        } else if (inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            zero[pairA] = PoweredUpHubRemote.readSensor(pairA);
            zero[pairB] = PoweredUpHubRemote.readSensor(pairB);
        } else if (y >= ROW_LEVELS && y < ROW_LEVELS + BUTTON_HEIGHT_MOTOR) {
            int level = (x - MARGIN) / (LEVEL_WIDTH + LEVEL_GAP);
            if (level >= STOP_COAST && level <= STOP_HOLD) {
                stopLevel = level;
            }
        } else {
            changePairPower(motorDelta(x, y, PAIR_ROW_A), motorDelta(x, y, PAIR_ROW_B));
        }
        drawPair();
    }

    /** With SYNC on, either row's button changes both motors with one linked command; otherwise each its own. */
    private void changePairPower(int deltaA, int deltaB) {
        if (pairSync && (deltaA != 0 || deltaB != 0)) {
            int shared = clamp(power[pairA] + (deltaA != 0 ? deltaA : deltaB));
            power[pairA] = shared;
            power[pairB] = shared;
            PoweredUpHubRemote.setLinkedMotorPower(link, shared, shared);
            return;
        }
        if (deltaA != 0) {
            power[pairA] = clamp(power[pairA] + deltaA);
            PoweredUpHubRemote.setMotorPower(pairA, power[pairA]);
        }
        if (deltaB != 0) {
            power[pairB] = clamp(power[pairB] + deltaB);
            PoweredUpHubRemote.setMotorPower(pairB, power[pairB]);
        }
    }

    private void toggleSync() {
        if (pairSync) {
            dropSync();
            return;
        }
        link = PoweredUpHubRemote.linkMotors(pairA, pairB);
        pairSync = link >= 0;
        if (pairSync) {
            power[pairB] = power[pairA];
            PoweredUpHubRemote.setLinkedMotorPower(link, power[pairA], power[pairA]);
        } else {
            Serial.println("The hub did not link the motors");
        }
    }

    private void dropSync() {
        if (pairSync) {
            PoweredUpHubRemote.unlinkMotors(link);
        }
        pairSync = false;
        link = -1;
    }

    /** Stops both motors of the pair with the chosen level. */
    private void stopPair() {
        if (stopLevel == STOP_HOLD) {
            PoweredUpHubRemote.holdMotor(pairA);
            PoweredUpHubRemote.holdMotor(pairB);
        } else if (pairSync && stopLevel == STOP_BRAKE) {
            PoweredUpHubRemote.brakeLinkedMotors(link);
        } else if (stopLevel == STOP_BRAKE) {
            PoweredUpHubRemote.brakeMotor(pairA);
            PoweredUpHubRemote.brakeMotor(pairB);
        } else if (pairSync) {
            PoweredUpHubRemote.setLinkedMotorPower(link, 0, 0);
        } else {
            PoweredUpHubRemote.setMotorPower(pairA, 0);
            PoweredUpHubRemote.setMotorPower(pairB, 0);
        }
        power[pairA] = 0;
        power[pairB] = 0;
    }

    /** The pair is the two lowest ports with a motor; if it changes, a link on the old one is dropped. */
    private void updatePair() {
        int first = -1;
        int second = -1;
        for (int port = 0; port < PORTS; port++) {
            if (isMotor(device[port])) {
                if (first < 0) {
                    first = port;
                } else if (second < 0) {
                    second = port;
                }
            }
        }
        if (first != pairA || second != pairB) {
            dropSync();
            pairA = first;
            pairB = second;
        }
    }

    private void toggleConnection() {
        if (PoweredUpHubRemote.isConnected()) {
            PoweredUpHubRemote.disconnect();
            Serial.println("Disconnected");
            linkLost();
        } else {
            drawStatus("Scanning...", TftTouchShield.YELLOW);
            if (PoweredUpHubRemote.connect(CONNECT_MILLIS)) {
                onConnected();
            } else {
                Serial.println("No hub found");
                linkLost();
            }
        }
    }

    private void onConnected() {
        Serial.println("Connected");
        hubType = PoweredUpHubRemote.hubType();
        resetPorts();
        if (hubType == PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            PoweredUpHubRemote.enableSensor(PoweredUpHubRemote.PORT_TECHNIC_TILT, PoweredUpHubRemote.MODE_IMU_VALUES);
        }
        PoweredUpHubRemote.setLedColor(LED_COLORS[ledIndex]);
        drawScreen();
    }

    /** If the hub went away on its own, show it the same way as a disconnect. */
    private void checkLink() {
        if (hubType != 0 && !PoweredUpHubRemote.isConnected()) {
            Serial.println("Link lost");
            linkLost();
        }
    }

    private void linkLost() {
        hubType = 0;
        resetPorts();
        drawScreen();
    }

    private void resetPorts() {
        for (int port = 0; port < PORTS; port++) {
            power[port] = 0;
            zero[port] = 0;
            device[port] = 0;
        }
        // The hub forgets its virtual ports with the connection.
        pairSync = false;
        link = -1;
        pairA = -1;
        pairB = -1;
    }

    /**
     * Asks the hub what it detected on each port. A motor found for the first time (or newly plugged in)
     * is subscribed to report its position; an unplugged one loses its power setting. Returns whether
     * anything changed, so the view can be redrawn from scratch.
     */
    private boolean detectDevices() {
        boolean changed = false;
        for (int port = 0; port < PORTS; port++) {
            int type = PoweredUpHubRemote.portDevice(port);
            if (type != device[port]) {
                device[port] = type;
                power[port] = 0;
                zero[port] = 0;
                if (isMotor(type)) {
                    PoweredUpHubRemote.enableSensor(port, PoweredUpHubRemote.MODE_MOTOR_POSITION);
                }
                changed = true;
            }
        }
        if (changed) {
            updatePair();
        }
        return changed;
    }

    private static int clamp(int power) {
        if (power > 100) {
            return 100;
        }
        if (power < -100) {
            return -100;
        }
        return power;
    }

    private static boolean inBox(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static int swatchX(int index) {
        return MARGIN + (index % 3) * (SWATCH_WIDTH + SWATCH_GAP);
    }

    private static int swatchY(int index) {
        return SWATCH_TOP + (index / 3) * (SWATCH_HEIGHT + SWATCH_GAP);
    }

    // ---- drawing ---------------------------------------------------------------------------

    private void drawScreen() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(MARGIN, 4);
        TftTouchShield.print("Powered Up");
        boolean connected = PoweredUpHubRemote.isConnected();
        drawStatus(connected ? "Connected" : "Not connected",
                connected ? TftTouchShield.GREEN : TftTouchShield.RED);
        drawTabs();
        drawConnectButton(connected);
        drawContent();
    }

    private void drawStatus(String text, int color) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, TftTouchShield.NAVY);
        TftTouchShield.setCursor(WIDTH - 90, 8);
        TftTouchShield.print("             ");
        TftTouchShield.setCursor(WIDTH - 90, 8);
        TftTouchShield.print(text);
    }

    private void drawTabs() {
        drawTab(VIEW_INFO, "INFO");
        drawTab(VIEW_LED, "LED");
        drawTab(VIEW_MOTORS, "MOTORS");
        drawTab(VIEW_PAIR, "PAIR");
    }

    private void drawTab(int tab, String label) {
        int color = tab == view ? TftTouchShield.BLUE : TftTouchShield.GRAY;
        TftTouchShield.fillRect(tab * TAB_WIDTH, TAB_Y, TAB_WIDTH, TAB_HEIGHT, color);
        TftTouchShield.drawRect(tab * TAB_WIDTH, TAB_Y, TAB_WIDTH, TAB_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(tab * TAB_WIDTH + 6, TAB_Y + 12);
        TftTouchShield.print(label);
    }

    private void drawConnectButton(boolean connected) {
        int color = connected ? TftTouchShield.RED : TftTouchShield.GREEN;
        TftTouchShield.fillRect(MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT, color);
        TftTouchShield.drawRect(MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(WIDTH / 2 - (connected ? 60 : 42), BOTTOM_Y + 8);
        TftTouchShield.print(connected ? "DISCONNECT" : "CONNECT");
    }

    /** Clears the area between the tabs and the connect button and draws the selected view. */
    private void drawContent() {
        TftTouchShield.fillRect(0, CONTENT_Y, WIDTH, CONTENT_HEIGHT, TftTouchShield.BLACK);
        if (!PoweredUpHubRemote.isConnected()) {
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
            TftTouchShield.setCursor(MARGIN, CONTENT_Y + 8);
            TftTouchShield.print("No hub connected");
        } else if (view == VIEW_INFO) {
            drawInfo();
        } else if (view == VIEW_LED) {
            drawColors();
        } else if (view == VIEW_MOTORS) {
            drawMotorButtons();
            drawMotorRows();
        } else {
            drawPair();
        }
    }

    /** Redraws only what changes by itself: the info values or the tacho values. */
    private void refresh() {
        if (!PoweredUpHubRemote.isConnected()) {
            return;
        }
        boolean changed = detectDevices();
        if (view == VIEW_INFO) {
            drawInfo();
        } else if ((view == VIEW_MOTORS || view == VIEW_PAIR) && changed) {
            drawContent();
        } else if (view == VIEW_MOTORS) {
            drawMotorRows();
        } else if (view == VIEW_PAIR && pairB >= 0) {
            drawPairRows();
        }
    }

    // ---- INFO view -------------------------------------------------------------------------

    /** The values read from the hub, redrawn in place (the background color overwrites the old text). */
    private void drawInfo() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, infoLine(0));
        printHubType();
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
        drawTilt();
    }

    private static int infoLine(int index) {
        return CONTENT_Y + 8 + index * 26;
    }

    private void printVersion(int version) {
        TftTouchShield.print(PoweredUpHubRemote.versionMajor(version));
        TftTouchShield.print(".");
        TftTouchShield.print(PoweredUpHubRemote.versionMinor(version));
        TftTouchShield.print(".");
        TftTouchShield.print(PoweredUpHubRemote.versionBugfix(version));
        TftTouchShield.print("   ");
    }

    private void printHubType() {
        if (hubType == PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            TftTouchShield.print("Technic Hub      ");
        } else if (hubType == PoweredUpHubRemote.TYPE_MOVE_HUB) {
            TftTouchShield.print("Move Hub         ");
        } else if (hubType == PoweredUpHubRemote.TYPE_CITY_HUB) {
            TftTouchShield.print("City Hub         ");
        } else {
            TftTouchShield.print("Hub 0x");
            TftTouchShield.print(hubType);
            TftTouchShield.print("        ");
        }
    }

    /** Tilt angles (degrees) from the Technic Hub's built-in sensor; other hubs have none. */
    private void drawTilt() {
        TftTouchShield.setCursor(MARGIN, infoLine(5));
        if (hubType != PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            TftTouchShield.print("Tilt n/a            ");
            return;
        }
        TftTouchShield.print("Tilt ");
        TftTouchShield.print(PoweredUpHubRemote.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 0, 2));
        TftTouchShield.print(" ");
        TftTouchShield.print(PoweredUpHubRemote.readSensorValue(PoweredUpHubRemote.PORT_TECHNIC_TILT, 1, 2));
        TftTouchShield.print("    ");
    }

    // ---- LED view --------------------------------------------------------------------------

    /** A 3x3 grid of swatches in roughly the colors the hub LED shows; the chosen one has a white frame. */
    private void drawColors() {
        for (int i = 0; i < LED_COLORS.length; i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            TftTouchShield.fillRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT, swatchColor(LED_COLORS[i]));
            int frame = i == ledIndex ? TftTouchShield.WHITE : TftTouchShield.BLACK;
            TftTouchShield.drawRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT, frame);
            TftTouchShield.drawRect(x + 1, y + 1, SWATCH_WIDTH - 2, SWATCH_HEIGHT - 2, frame);
        }
    }

    private static int swatchColor(int hubColor) {
        if (hubColor == PoweredUpHubRemote.COLOR_RED) {
            return TftTouchShield.RED;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_ORANGE) {
            return TftTouchShield.ORANGE;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_YELLOW) {
            return TftTouchShield.YELLOW;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_GREEN) {
            return TftTouchShield.GREEN;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_CYAN) {
            return TftTouchShield.CYAN;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_BLUE) {
            return TftTouchShield.BLUE;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_PURPLE) {
            return TftTouchShield.MAGENTA;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_PINK) {
            return TftTouchShield.color(255, 128, 192);
        }
        return TftTouchShield.WHITE;
    }

    // ---- MOTORS view -----------------------------------------------------------------------

    private void drawButton(int x, int y, int width, int color, String label, int textX) {
        TftTouchShield.fillRect(x, y, width, BUTTON_HEIGHT_MOTOR, color);
        TftTouchShield.drawRect(x, y, width, BUTTON_HEIGHT_MOTOR, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(textX, y + 9);
        TftTouchShield.print(label);
    }

    /** The buttons that stay the same until a device changes: STOP, ZERO and the - / + of each motor. */
    private void drawMotorButtons() {
        drawButton(MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.RED, "STOP", MARGIN + 28);
        drawButton(WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.GRAY, "ZERO",
                WIDTH - MARGIN - WIDE_BUTTON_WIDTH + 28);
        for (int port = 0; port < PORTS; port++) {
            if (isMotor(device[port])) {
                drawButton(MARGIN, rowOf(port), SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "-", MARGIN + 20);
                drawButton(WIDTH - MARGIN - SMALL_BUTTON_WIDTH, rowOf(port), SMALL_BUTTON_WIDTH,
                        TftTouchShield.BLUE, "+", WIDTH - MARGIN - SMALL_BUTTON_WIDTH + 20);
            }
        }
    }

    private void drawMotorRows() {
        for (int port = 0; port < PORTS; port++) {
            drawPortRow(port, rowOf(port));
        }
    }

    /**
     * One port: its letter and the detected device, then for a motor its power and tacho value (position
     * minus the zero offset) between the - and + buttons.
     */
    private void drawPortRow(int port, int row) {
        int x = MARGIN + SMALL_BUTTON_WIDTH + 8;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (!isMotor(device[port])) {
            TftTouchShield.setCursor(MARGIN, row + 8);
            TftTouchShield.print("Port ");
            TftTouchShield.print(portLetter(port));
            TftTouchShield.print("  ");
            printDevice(device[port]);
            return;
        }
        TftTouchShield.setCursor(x, row);
        TftTouchShield.print(portLetter(port));
        TftTouchShield.print(": ");
        printDevice(device[port]);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(x, row + 10);
        TftTouchShield.print(power[port]);
        TftTouchShield.print("%   ");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(x, row + 26);
        TftTouchShield.print("tacho ");
        TftTouchShield.print(PoweredUpHubRemote.readSensor(port) - zero[port]);
        TftTouchShield.print("     ");
    }

    private static String portLetter(int port) {
        if (port == PoweredUpHubRemote.PORT_A) {
            return "A";
        }
        if (port == PoweredUpHubRemote.PORT_B) {
            return "B";
        }
        if (port == PoweredUpHubRemote.PORT_C) {
            return "C";
        }
        return "D";
    }

    /**
     * The device's name, short enough (at most 18 characters) to end before the + button of a motor row.
     * It is not padded: a changed device redraws the whole view first, so no older text is left behind.
     */
    private static void printDevice(int type) {
        if (type == PoweredUpHubRemote.DEVICE_NONE) {
            TftTouchShield.print("-- empty --");
        } else if (isMotor(type)) {
            printMotorName(type);
        } else if (type == PoweredUpHubRemote.DEVICE_COLOR_DISTANCE_SENSOR) {
            TftTouchShield.print("Color/dist. sensor");
        } else if (type == PoweredUpHubRemote.DEVICE_MOTION_SENSOR) {
            TftTouchShield.print("Motion sensor");
        } else {
            TftTouchShield.print("Device 0x");
            TftTouchShield.print(type);
        }
    }

    private static void printMotorName(int type) {
        if (type == PoweredUpHubRemote.DEVICE_TECHNIC_XL_MOTOR) {
            TftTouchShield.print("Technic XL motor");
        } else if (type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_MOTOR
                || type == PoweredUpHubRemote.DEVICE_LARGE_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR) {
            TftTouchShield.print("Large motor");
        } else if (type == PoweredUpHubRemote.DEVICE_MEDIUM_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR) {
            TftTouchShield.print("Medium ang. motor");
        } else {
            TftTouchShield.print("Medium motor");
        }
    }

    // ---- PAIR view drawing -----------------------------------------------------------------

    /** The whole view: the SYNC switch, the two motors, the stop level choice and the STOP button. */
    private void drawPair() {
        TftTouchShield.fillRect(0, CONTENT_Y, WIDTH, CONTENT_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (pairB < 0) {
            TftTouchShield.setCursor(MARGIN, CONTENT_Y + 8);
            TftTouchShield.print("Plug in two motors");
            TftTouchShield.setCursor(MARGIN, CONTENT_Y + 34);
            TftTouchShield.print("to use a pair");
            return;
        }
        TftTouchShield.setCursor(MARGIN, PAIR_ROW_SYNC + 10);
        TftTouchShield.print(portLetter(pairA));
        TftTouchShield.print(" + ");
        TftTouchShield.print(portLetter(pairB));
        drawButton(WIDTH - MARGIN - WIDE_BUTTON_WIDTH, PAIR_ROW_SYNC, WIDE_BUTTON_WIDTH,
                pairSync ? TftTouchShield.GREEN : TftTouchShield.GRAY, pairSync ? "SYNC ON" : "SYNC OFF",
                WIDTH - MARGIN - WIDE_BUTTON_WIDTH + (pairSync ? 14 : 8));
        drawPlusMinus(PAIR_ROW_A);
        drawPlusMinus(PAIR_ROW_B);
        drawLevel(STOP_COAST, "COAST");
        drawLevel(STOP_BRAKE, "BRAKE");
        drawLevel(STOP_HOLD, "HOLD");
        drawButton(MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.RED, "STOP", MARGIN + 28);
        drawButton(WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.GRAY, "ZERO",
                WIDTH - MARGIN - WIDE_BUTTON_WIDTH + 28);
        drawPairRows();
    }

    private void drawPlusMinus(int row) {
        drawButton(MARGIN, row, SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "-", MARGIN + 20);
        drawButton(WIDTH - MARGIN - SMALL_BUTTON_WIDTH, row, SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "+",
                WIDTH - MARGIN - SMALL_BUTTON_WIDTH + 20);
    }

    private void drawLevel(int level, String label) {
        drawButton(MARGIN + level * (LEVEL_WIDTH + LEVEL_GAP), ROW_LEVELS, LEVEL_WIDTH,
                level == stopLevel ? TftTouchShield.BLUE : TftTouchShield.GRAY, label,
                MARGIN + level * (LEVEL_WIDTH + LEVEL_GAP) + (level == STOP_HOLD ? 12 : 6));
    }

    private void drawPairRows() {
        drawPortRow(pairA, PAIR_ROW_A);
        drawPortRow(pairB, PAIR_ROW_B);
    }
}
