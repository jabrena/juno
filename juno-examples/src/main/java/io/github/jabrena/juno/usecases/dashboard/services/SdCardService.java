package io.github.jabrena.juno.usecases.dashboard.services;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.SdCapacity;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.StatusFile;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Everything the dashboard does with the SD card, publishing each result into the model. The card is not safe to use
 * from two threads, so after {@link #begin()} only this service's own thread touches it: the quick reads first, so
 * the Wi-Fi service gets its credentials early, then the slow capacity count.
 */
public final class SdCardService {
    private static final String STATUS_FILE = "STATUS.TXT";

    private final TftDashboardState model;
    private boolean statusFileWritten;

    public SdCardService(TftDashboardState model) {
        this.model = model;
    }

    /** Starts the card and logs the start-up to STATUS.TXT; on the main thread, before {@link #start()}. */
    public void begin() {
        model.publishCardReady(SdCard.begin());
        if (!model.cardReady()) {
            return;
        }
        statusFileWritten = SdCard.appendLine(STATUS_FILE, "dashboard startup");
        if (!statusFileWritten) {
            Serial.println("Could not write STATUS.TXT");
        }
    }

    /** Starts the thread that reads the card and publishes what it finds. */
    public void start() {
        Thread.startVirtualThread(this::run);
    }

    private void run() {
        model.publishStatusFile(new StatusFile(statusFileWritten ? SdCard.size(STATUS_FILE) : -1));
        model.publishCredentials(loadCredentials());
        model.publishSdCapacity(new SdCapacity(SdCard.totalKiB(), SdCard.freeKiB()));
    }

    /** The Wi-Fi credentials from application.properties on the card, or null when there are none. */
    private WifiCredentials loadCredentials() {
        if (!model.cardReady()) {
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
}
