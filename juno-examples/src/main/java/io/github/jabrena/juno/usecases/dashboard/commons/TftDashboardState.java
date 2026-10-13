package io.github.jabrena.juno.usecases.dashboard.commons;

/**
 * The memory the dashboard's screens share: what the SD-card thread and the Wi-Fi thread found out, read by both the
 * network status and the storage screens. Each value is published once, as a whole record, by the thread that owns
 * it; the display loop only reads. A value is null until its thread has published it.
 */
public final class TftDashboardState {
    private static final int POLL_MS = 20;

    /** The size of STATUS.TXT in bytes, negative when it could not be read. */
    public record StatusFile(int bytes) {
        public boolean readable() {
            return bytes >= 0;
        }
    }

    /** The card's size and free space, in KiB; either is negative when the card could not be read. */
    public record SdCapacity(int totalKiB, int freeKiB) {
        public boolean known() {
            return totalKiB > 0 && freeKiB >= 0;
        }
    }

    public record WifiCredentials(String ssid, String password) {
    }

    public record WifiStatus(boolean connected) {
    }

    private volatile boolean cardReady;
    private volatile StatusFile statusFile;
    private volatile SdCapacity sdCapacity;
    private volatile WifiCredentials credentials;
    private volatile boolean credentialsLoaded;
    private volatile WifiStatus wifiStatus;

    public boolean cardReady() {
        return cardReady;
    }

    public StatusFile statusFile() {
        return statusFile;
    }

    public SdCapacity sdCapacity() {
        return sdCapacity;
    }

    public WifiStatus wifiStatus() {
        return wifiStatus;
    }

    /** Set once on the main thread, before either background thread starts. */
    public void publishCardReady(boolean ready) {
        cardReady = ready;
    }

    public void publishStatusFile(StatusFile file) {
        statusFile = file;
    }

    public void publishSdCapacity(SdCapacity capacity) {
        sdCapacity = capacity;
    }

    /** The SD thread hands over the credentials, or null when the card has none; the Wi-Fi thread waits for this. */
    public void publishCredentials(WifiCredentials found) {
        credentials = found;
        credentialsLoaded = true;
    }

    public void publishWifiStatus(WifiStatus status) {
        wifiStatus = status;
    }

    /** Waits, without holding up other threads, until the SD thread has looked for the credentials. */
    public WifiCredentials awaitCredentials() throws InterruptedException {
        while (!credentialsLoaded) {
            Thread.sleep(POLL_MS);
        }
        return credentials;
    }
}
