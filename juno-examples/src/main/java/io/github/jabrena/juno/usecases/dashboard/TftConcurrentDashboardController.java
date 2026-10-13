package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.net.http.HttpsClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Coordinates the dashboard's SD-card, Wi-Fi, and HTTPS operations. */
final class TftConcurrentDashboardController {
    static final int STATUS_WAITING = 0;
    static final int STATUS_RUNNING = 1;
    static final int STATUS_SUCCEEDED = 2;
    static final int STATUS_FAILED = 3;

    private static final int HTTPS_PORT = 443;
    private static final int BODY_CAPACITY = 256;
    private static final int HEADERS_CAPACITY = 128;
    private static final int WORKER_POLL_MS = 20;
    private static final int MAX_CONNECTION_ATTEMPTS = 10;
    private static final String STATUS_FILE = "STATUS.TXT";

    private static final byte[] BODY = new byte[BODY_CAPACITY];
    private static final byte[] HEADERS = new byte[HEADERS_CAPACITY];
    private static final int[] RESPONSE_INFO = new int[2];

    private volatile int httpsState = STATUS_WAITING;
    private volatile int sdState = STATUS_WAITING;
    private volatile int sdCapacityState = STATUS_WAITING;
    private volatile int sdTotalKiB = -1;
    private volatile int sdFreeKiB = -1;
    private volatile boolean httpsRequested;
    private volatile int completedRequests;

    private boolean sdReady;
    private boolean statusFileReady;
    private boolean wifiReady;
    private int httpStatus;
    private int responseBytes;
    private int statusFileBytes = -1;

    boolean beginSdCard() {
        sdReady = SdCard.begin();
        return sdReady;
    }

    void writeStartupStatus() {
        statusFileReady = appendStartupStatus();
    }

    void markSdUnavailable() {
        sdState = STATUS_FAILED;
        sdCapacityState = STATUS_FAILED;
    }

    void beginNetworkAndWorker() throws IOException {
        wifiReady = connectWifi();
        if (!wifiReady) {
            httpsState = STATUS_FAILED;
        }
        Thread.startVirtualThread(this::backgroundCoordinator);
    }

    void requestHttps() {
        if (!wifiReady) {
            httpsState = STATUS_FAILED;
        } else if (httpsState != STATUS_RUNNING) {
            httpStatus = 0;
            responseBytes = 0;
            httpsState = STATUS_RUNNING;
            httpsRequested = true;
        }
    }

    int httpsState() {
        return httpsState;
    }

    int sdState() {
        return sdState;
    }

    boolean wifiReady() {
        return wifiReady;
    }

    int httpStatus() {
        return httpStatus;
    }

    int responseBytes() {
        return responseBytes;
    }

    int statusFileBytes() {
        return statusFileBytes;
    }

    int sdCapacityState() {
        return sdCapacityState;
    }

    int sdTotalKiB() {
        return sdTotalKiB;
    }

    int sdFreeKiB() {
        return sdFreeKiB;
    }

    int completedRequests() {
        return completedRequests;
    }

    void printMetrics(int framesPerSecond) {
        Serial.print("HTTPS status: ");
        Serial.println(httpStatus);
        Serial.print("HTTPS body bytes: ");
        Serial.println(responseBytes);
        Serial.print("STATUS.TXT bytes: ");
        Serial.println(statusFileBytes);
        Serial.print("FPS: ");
        Serial.println(framesPerSecond);
    }

    private void backgroundCoordinator() {
        if (statusFileReady) {
            sdState = STATUS_RUNNING;
            readStatusFile();
        }
        if (sdReady) {
            scanSdCapacity();
        }

        while (true) {
            if (httpsRequested) {
                httpsRequested = false;
                runHttpsRequest();
            }
            try {
                Thread.sleep(WORKER_POLL_MS);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }

    private void runHttpsRequest() {
        fetchHttps();
        completedRequests = completedRequests + 1;
    }

    private boolean appendStartupStatus() {
        boolean written = SdCard.appendLine(STATUS_FILE, "dashboard startup");
        if (!written) {
            sdState = STATUS_FAILED;
            Serial.println("Could not write STATUS.TXT");
        }
        return written;
    }

    private boolean connectWifi() throws IOException {
        WifiCredentials credentials = loadCredentials();
        if (credentials == null) {
            return false;
        }

        Wifi.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        return Wifi.status() == Wifi.STATUS_CONNECTED;
    }

    private WifiCredentials loadCredentials() throws IOException {
        if (!sdReady) {
            Serial.println("WiFi settings unavailable without SD card");
            return null;
        }

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
    }

    private void fetchHttps() {
        int bytes = HttpsClient.get(
                "example.com", HTTPS_PORT, "/", BODY, BODY_CAPACITY, HEADERS, HEADERS_CAPACITY, RESPONSE_INFO);
        responseBytes = bytes;
        httpStatus = RESPONSE_INFO[0];
        httpsState = bytes >= 0 && httpStatus >= 200 && httpStatus < 400 ? STATUS_SUCCEEDED : STATUS_FAILED;
    }

    private void readStatusFile() {
        int bytes = SdCard.size(STATUS_FILE);
        statusFileBytes = bytes;
        sdState = bytes >= 0 ? STATUS_SUCCEEDED : STATUS_FAILED;
    }

    /** Counting free space reads the whole allocation table, so it runs once, here, off the display thread. */
    private void scanSdCapacity() {
        sdCapacityState = STATUS_RUNNING;
        int total = SdCard.totalKiB();
        int free = SdCard.freeKiB();
        sdTotalKiB = total;
        sdFreeKiB = free;
        sdCapacityState = total > 0 && free >= 0 ? STATUS_SUCCEEDED : STATUS_FAILED;
    }

    private record WifiCredentials(String ssid, String password) {
    }
}
