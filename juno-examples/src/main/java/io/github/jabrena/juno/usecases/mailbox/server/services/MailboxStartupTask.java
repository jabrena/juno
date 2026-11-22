package io.github.jabrena.juno.usecases.mailbox.server.services;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.mailbox.server.commons.MailboxStartupState;
import io.github.jabrena.juno.usecases.mailbox.server.commons.MailboxUiState;
import io.github.jabrena.juno.usecases.mailbox.server.security.MailboxSecurity;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import javax.net.ssl.SSLContext;

/** Performs blocking SD and TLS bootstrap away from the TFT task, then starts the HTTPS server. */
public final class MailboxStartupTask implements Runnable {
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final int HTTPS_PORT = 443;
    private static final int STORAGE_ATTEMPTS = 5;
    private static final int STORAGE_RETRY_MILLIS = 500;

    private final SdCard sdCard;
    private final MailboxModel model;
    private final MailboxSecurity security;
    private final MailboxStartupState state;
    private final MailboxUiState uiState;
    private final MailboxController controller;

    public MailboxStartupTask(SdCard sdCard, MailboxModel model, MailboxSecurity security, MailboxStartupState state,
                       MailboxUiState uiState, MailboxController controller) {
        this.sdCard = sdCard;
        this.model = model;
        this.security = security;
        this.state = state;
        this.uiState = uiState;
        this.controller = controller;
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
        SSLContext context = new MailboxCredentials(sdCard).load();
        if (context == null) {
            stop("server.crt or server.key is missing from the SD card or does not parse");
            return;
        }
        try {
            HttpsServer server = HttpsServer.create(new InetSocketAddress(HTTPS_PORT), 4);
            server.setHttpsConfigurator(new HttpsConfigurator(context));
            server.createContext("/", controller::handle);
            server.start();
        } catch (IOException | IllegalStateException exception) {
            stop("the HTTPS server did not start: certificate or key did not parse");
            return;
        }
        state.ready();
        Serial.println("Mailbox listening on HTTPS port 443");
        Thread.startVirtualThread(new MailboxRestoreTask(model, uiState));
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
