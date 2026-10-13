package io.github.jabrena.juno.usecases.dashboard.status;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardScreen;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.StatusFile;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiCredentials;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiStatus;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftHeaderWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftTextWidget;

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
    private static final String STATUS_FILE = "STATUS.TXT";
    private static final int MAX_CONNECTION_ATTEMPTS = 10;

    private static final int LABEL_X = 12;
    private static final int VALUE_X = 90;
    private static final int WIFI_Y = 56;
    private static final int SD_Y = 92;
    private static final int FILE_Y = 128;

    private final TftDashboardScreen home;
    private final TftDashboardState state;
    private final TftFpsWidget fps;
    private final TftHeaderWidget header = new TftHeaderWidget("NETWORK STATUS");
    private final TftButtonWidget homeButton = new TftButtonWidget(196, 200, 112, 34, "HOME", TftTouchShield.GRAY);
    private final TftTextWidget wifiLabel = new TftTextWidget(LABEL_X, WIFI_Y, 2);
    private final TftTextWidget sdLabel = new TftTextWidget(LABEL_X, SD_Y, 2);
    private final TftTextWidget fileLabel = new TftTextWidget(LABEL_X, FILE_Y, 2);
    private final TftTextWidget wifiValue = new TftTextWidget(VALUE_X, WIFI_Y, 2);
    private final TftTextWidget sdValue = new TftTextWidget(VALUE_X, SD_Y, 2);
    private final TftTextWidget fileValue = new TftTextWidget(VALUE_X, FILE_Y, 2);

    private boolean statusFileWritten;

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
        wifiLabel.draw("WIFI", TftTouchShield.WHITE);
        sdLabel.draw("SD", TftTouchShield.WHITE);
        fileLabel.draw("FILE", TftTouchShield.WHITE);
        homeButton.draw();
        fps.reset();
        wifiValue.reset();
        sdValue.reset();
        fileValue.reset();
    }

    @Override
    public void draw() {
        StatusFile file = state.statusFile();
        WifiStatus wifi = state.wifiStatus();
        if (wifi == null) {
            wifiValue.set("STARTING", TftTouchShield.YELLOW);
        } else if (wifi.connected()) {
            wifiValue.set("OK", TftTouchShield.GREEN);
        } else {
            wifiValue.set("FAILED", TftTouchShield.RED);
        }
        if (!state.cardReady() || !statusFileWritten) {
            sdValue.set("FAILED", TftTouchShield.RED);
        } else if (file == null) {
            sdValue.set("STARTING", TftTouchShield.YELLOW);
        } else if (file.readable()) {
            sdValue.set("OK", TftTouchShield.GREEN);
        } else {
            sdValue.set("FAILED", TftTouchShield.RED);
        }
        if (file == null || !file.readable()) {
            fileValue.set("-", TftTouchShield.WHITE);
        } else {
            fileValue.set(file.bytes(), " B", TftTouchShield.WHITE);
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
}
