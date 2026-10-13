package io.github.jabrena.juno.usecases.dashboard.status;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardScreen;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.StatusFile;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiCredentials;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiStatus;
import io.github.jabrena.juno.usecases.dashboard.commons.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.TftHeaderWidget;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The network status screen: Wi-Fi, SD card and STATUS.TXT, read from the shared {@link TftDashboardState} and each
 * line redrawn only when its value changes. It also provides the services behind those lines, each returning a
 * record: the size of STATUS.TXT and the Wi-Fi credentials, which the SD thread calls, and the Wi-Fi connection,
 * which the Wi-Fi thread calls.
 */
public final class TftDashboardStatusView implements TftDashboardScreen {
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


    private final TftDashboardScreen home;
    private final TftDashboardState state;
    private final TftFpsWidget fps;
    private final TftHeaderWidget header = new TftHeaderWidget("NETWORK STATUS");
    private final TftButtonWidget homeButton = new TftButtonWidget(196, 200, 112, 34, "HOME", TftTouchShield.GRAY);

    private boolean statusFileWritten;

    private int shownWifiState = UNSHOWN;
    private int shownSdState = UNSHOWN;
    private int shownFileBytes = UNSHOWN;

    public TftDashboardStatusView(TftDashboardScreen home, TftDashboardState state, TftFpsWidget fps) {
        this.home = home;
        this.state = state;
        this.fps = fps;
    }

    /** Logs the start-up to STATUS.TXT; called once, on the main thread, after the card was started. */
    public void writeStartupStatus() {
        if (!state.cardReady()) {
            return;
        }
        statusFileWritten = SdCard.appendLine(STATUS_FILE, "dashboard startup");
        if (!statusFileWritten) {
            Serial.println("Could not write STATUS.TXT");
        }
    }

    /** Service: the size of STATUS.TXT; a quick read of the card. */
    public StatusFile readStatusFile() {
        return new StatusFile(statusFileWritten ? SdCard.size(STATUS_FILE) : -1);
    }

    /** Service: the Wi-Fi credentials from application.properties on the card, or null when there are none. */
    public WifiCredentials loadCredentials() {
        if (!state.cardReady()) {
            Serial.println("WiFi settings unavailable without SD card");
            return null;
        }
        try {
            InputStream file = SdCard.open("application.properties");
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            file.close();
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return null;
            }
            return new WifiCredentials(ssid, password);
        } catch (IOException failed) {
            Serial.println("Could not read application.properties");
            return null;
        }
    }

    /** Service: joins the network, which can take several seconds; it never touches the card. */
    public WifiStatus connectWifi(WifiCredentials credentials) {
        if (credentials == null) {
            return new WifiStatus(false);
        }
        Wifi.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        return new WifiStatus(Wifi.status() == Wifi.STATUS_CONNECTED);
    }

    @Override
    public void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        header.draw();

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, WIFI_Y);
        TftTouchShield.print("WIFI");
        TftTouchShield.setCursor(12, SD_Y);
        TftTouchShield.print("SD");
        TftTouchShield.setCursor(12, FILE_Y);
        TftTouchShield.print("FILE");
        homeButton.draw();
        fps.reset();

        shownWifiState = UNSHOWN;
        shownSdState = UNSHOWN;
        shownFileBytes = UNSHOWN;
    }

    @Override
    public void draw() {
        StatusFile file = state.statusFile();
        WifiStatus wifi = state.wifiStatus();
        int wifiState = wifi == null ? STATE_RUNNING : wifi.connected() ? STATE_OK : STATE_FAILED;
        if (wifiState != shownWifiState) {
            shownWifiState = wifiState;
            drawState(wifiState, WIFI_Y);
        }
        int sdState = !state.cardReady() || !statusFileWritten ? STATE_FAILED
                : file == null ? STATE_RUNNING : file.readable() ? STATE_OK : STATE_FAILED;
        if (sdState != shownSdState) {
            shownSdState = sdState;
            drawState(sdState, SD_Y);
        }
        int fileBytes = file == null ? -1 : file.bytes();
        if (fileBytes != shownFileBytes) {
            shownFileBytes = fileBytes;
            drawFileBytes(fileBytes);
        }
        fps.update();
    }

    @Override
    public TftDashboardScreen touch(int x, int y) {
        if (homeButton.contains(x, y)) {
            return home;
        }
        return this;
    }

    private void drawState(int state, int y) {
        TftTouchShield.fillRect(VALUE_X, y, VALUE_WIDTH, LINE_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(stateColor(state), TftTouchShield.NAVY);
        TftTouchShield.setCursor(VALUE_X, y);
        if (state == STATE_RUNNING) {
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
