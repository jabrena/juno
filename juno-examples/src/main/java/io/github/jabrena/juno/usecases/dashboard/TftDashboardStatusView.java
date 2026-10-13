package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The network status screen: Wi-Fi, SD card and STATUS.TXT, each line redrawn only when its value changes. It also
 * does the work behind those lines: it logs the start-up to STATUS.TXT, and on the background thread reads the
 * file's size and connects Wi-Fi with the credentials from the card.
 */
final class TftDashboardStatusView implements TftDashboardScreen {
    private static final int STATE_WAITING = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_OK = 2;
    private static final int STATE_FAILED = 3;

    private static final String STATUS_FILE = "STATUS.TXT";
    private static final int MAX_CONNECTION_ATTEMPTS = 10;

    private static final int VALUE_X = 90;
    private static final int VALUE_WIDTH = 218;
    private static final int LINE_HEIGHT = 16;
    private static final int WIFI_Y = 56;
    private static final int SD_Y = 92;
    private static final int FILE_Y = 128;
    private static final int UNSHOWN = Integer.MIN_VALUE;

    private static final int HOME_BUTTON_X = 196;
    private static final int HOME_BUTTON_Y = 200;
    private static final int HOME_BUTTON_WIDTH = 112;
    private static final int HOME_BUTTON_HEIGHT = 34;

    private final TftDashboardScreen home;

    // Written by the background thread, read by the display loop.
    private volatile int wifiState = STATE_WAITING;
    private volatile int sdState = STATE_WAITING;
    private volatile int statusFileBytes = -1;

    private boolean sdReady;
    private boolean statusFileWritten;

    private int shownWifiState = UNSHOWN;
    private int shownSdState = UNSHOWN;
    private int shownFileBytes = UNSHOWN;

    TftDashboardStatusView(TftDashboardScreen home) {
        this.home = home;
    }

    /** Logs the start-up to STATUS.TXT; called once, on the main thread, after the card was started. */
    void writeStartupStatus(boolean cardReady) {
        sdReady = cardReady;
        if (!cardReady) {
            sdState = STATE_FAILED;
            return;
        }
        statusFileWritten = SdCard.appendLine(STATUS_FILE, "dashboard startup");
        if (!statusFileWritten) {
            sdState = STATE_FAILED;
            Serial.println("Could not write STATUS.TXT");
        }
    }

    /** Runs on the background thread. */
    void readStatusFile() {
        if (!statusFileWritten) {
            return;
        }
        sdState = STATE_RUNNING;
        int bytes = SdCard.size(STATUS_FILE);
        statusFileBytes = bytes;
        sdState = bytes >= 0 ? STATE_OK : STATE_FAILED;
    }

    /** Runs on the background thread: joining the network can take several seconds. */
    void connectWifi() {
        wifiState = STATE_RUNNING;
        boolean connected;
        try {
            connected = joinNetwork();
        } catch (IOException failed) {
            connected = false;
        }
        wifiState = connected ? STATE_OK : STATE_FAILED;
    }

    @Override
    public void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print("NETWORK STATUS");
        TftTouchShield.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);

        TftTouchShield.setCursor(12, WIFI_Y);
        TftTouchShield.print("WIFI");
        TftTouchShield.setCursor(12, SD_Y);
        TftTouchShield.print("SD");
        TftTouchShield.setCursor(12, FILE_Y);
        TftTouchShield.print("FILE");
        drawHomeButton();

        shownWifiState = UNSHOWN;
        shownSdState = UNSHOWN;
        shownFileBytes = UNSHOWN;
    }

    @Override
    public void draw() {
        int wifi = wifiState;
        if (wifi != shownWifiState) {
            shownWifiState = wifi;
            drawState(wifi, WIFI_Y);
        }
        int sd = sdState;
        if (sd != shownSdState) {
            shownSdState = sd;
            drawState(sd, SD_Y);
        }
        int fileBytes = statusFileBytes;
        if (fileBytes != shownFileBytes) {
            shownFileBytes = fileBytes;
            drawFileBytes(fileBytes);
        }
    }

    @Override
    public TftDashboardScreen touch(int x, int y) {
        if (homeButtonContains(x, y)) {
            return home;
        }
        return this;
    }

    private boolean joinNetwork() throws IOException {
        if (!sdReady) {
            Serial.println("WiFi settings unavailable without SD card");
            return false;
        }
        InputStream file = SdCard.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return false;
        }
        Properties properties = new Properties();
        properties.load(file);
        file.close();
        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return false;
        }

        Wifi.begin(ssid, password);
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        return Wifi.status() == Wifi.STATUS_CONNECTED;
    }

    private void drawHomeButton() {
        TftTouchShield.fillRect(HOME_BUTTON_X, HOME_BUTTON_Y, HOME_BUTTON_WIDTH, HOME_BUTTON_HEIGHT,
                TftTouchShield.GRAY);
        TftTouchShield.drawRect(HOME_BUTTON_X, HOME_BUTTON_Y, HOME_BUTTON_WIDTH, HOME_BUTTON_HEIGHT,
                TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(228, 209);
        TftTouchShield.print("HOME");
    }

    private boolean homeButtonContains(int x, int y) {
        return x >= HOME_BUTTON_X
                && x < HOME_BUTTON_X + HOME_BUTTON_WIDTH
                && y >= HOME_BUTTON_Y
                && y < HOME_BUTTON_Y + HOME_BUTTON_HEIGHT;
    }

    private void drawState(int state, int y) {
        TftTouchShield.fillRect(VALUE_X, y, VALUE_WIDTH, LINE_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(stateColor(state), TftTouchShield.NAVY);
        TftTouchShield.setCursor(VALUE_X, y);
        if (state == STATE_WAITING) {
            TftTouchShield.print("WAITING");
        } else if (state == STATE_RUNNING) {
            TftTouchShield.print("STARTING");
        } else if (state == STATE_OK) {
            TftTouchShield.print("OK");
        } else {
            TftTouchShield.print("FAILED");
        }
    }

    private void drawFileBytes(int bytes) {
        TftTouchShield.fillRect(VALUE_X, FILE_Y, VALUE_WIDTH, LINE_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(VALUE_X, FILE_Y);
        if (bytes < 0) {
            TftTouchShield.print("-");
        } else {
            TftTouchShield.print(bytes);
            TftTouchShield.print(" B");
        }
    }

    private int stateColor(int state) {
        if (state == STATE_OK) {
            return TftTouchShield.GREEN;
        }
        if (state == STATE_FAILED) {
            return TftTouchShield.RED;
        }
        if (state == STATE_RUNNING) {
            return TftTouchShield.YELLOW;
        }
        return TftTouchShield.GRAY;
    }
}
