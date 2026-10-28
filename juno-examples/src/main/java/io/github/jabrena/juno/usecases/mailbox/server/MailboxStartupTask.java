package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Performs blocking SD and TLS bootstrap away from the TFT task, then hands the same task to HTTPS serving. */
final class MailboxStartupTask implements Runnable {
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final int HTTPS_PORT = 443;
    private static final int STORAGE_ATTEMPTS = 5;
    private static final int STORAGE_RETRY_MILLIS = 500;

    private final SdCard sdCard;
    private final MailboxModel model;
    private final MailboxSecurity security;
    private final MailboxStartupState state;
    private final MailboxUiState uiState;
    private final MailboxServerTask serverTask;

    MailboxStartupTask(SdCard sdCard, MailboxModel model, MailboxSecurity security, MailboxStartupState state,
                       MailboxUiState uiState, MailboxServerTask serverTask) {
        this.sdCard = sdCard;
        this.model = model;
        this.security = security;
        this.state = state;
        this.uiState = uiState;
        this.serverTask = serverTask;
    }

    @Override
    public void run() {
        Serial.println("Mailbox storage bootstrap");
        if (!beginStorage()) {
            stop("SD card is unavailable");
            return;
        }
        Serial.println("Mailbox storage ready");
        security.begin();
        if (!loadSettings()) {
            stop("application.properties could not be read");
            return;
        }

        Serial.println("Mailbox TLS bootstrap");
        MailboxCredentials credentials = new MailboxCredentials(sdCard);
        if (!credentials.load()) {
            stop("server.crt or server.key is missing from the SD card");
            return;
        }
        if (!HttpServer.beginTls(HTTPS_PORT, credentials.certificate(), credentials.certificateLength(),
                credentials.privateKey(), credentials.privateKeyLength())) {
            stop("certificate or key did not parse");
            return;
        }
        credentials.wipePrivateKey();
        state.ready();
        Serial.println("Mailbox listening on HTTPS port 443");
        Thread.startVirtualThread(new MailboxRestoreTask(model, uiState));
        serverTask.run();
    }

    private boolean beginStorage() {
        for (int attempt = 0; attempt < STORAGE_ATTEMPTS; attempt++) {
            model.beginStorage();
            if (model.storageReady()) {
                return true;
            }
            if (attempt + 1 < STORAGE_ATTEMPTS) {
                Serial.println("SD retry");
                Delay.millis(STORAGE_RETRY_MILLIS);
            }
        }
        return false;
    }

    /** Whether {@code application.properties} could be read; a missing file is not an error. */
    private boolean loadSettings() {
        try (InputStream file = sdCard.open(APPLICATION_PROPERTIES)) {
            if (file == null) {
                Serial.println("application.properties not found");
                return true;
            }
            Properties properties = new Properties();
            properties.load(file);
            return true;
        } catch (IOException exception) {
            Serial.println("Could not read application.properties");
            return false;
        }
    }

    private void stop(String reason) {
        state.failed();
        Serial.print("Mailbox stopped: ");
        Serial.println(reason);
    }
}
