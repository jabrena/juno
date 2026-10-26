package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;

/** The blocking Q HTTPS loop, isolated from the main task that owns the mailbox TFT and touch panel. */
final class MailboxServerTask implements Runnable {
    private final MailboxController controller;
    private final byte[] requestBody;
    private final Clock clock;

    MailboxServerTask(MailboxController controller, byte[] requestBody, Clock clock) {
        this.controller = controller;
        this.requestBody = requestBody;
        this.clock = clock;
    }

    @Override
    public void run() {
        while (true) {
            int acceptStarted = clock.millis();
            int bodyLength = HttpServer.accept(requestBody, requestBody.length);
            if (bodyLength < 0) {
                continue;
            }
            // One line per request: the time to read it and the time to answer it, to find where clients wait.
            int handleStarted = clock.millis();
            String method = HttpServer.method();
            controller.handle(method, HttpServer.path(), HttpServer.contentLength(), requestBody, bodyLength);
            Serial.print("HTTPS ");
            Serial.print(method);
            Serial.print(" read ms ");
            Serial.print(handleStarted - acceptStarted);
            Serial.print(" handle ms ");
            Serial.println(clock.millis() - handleStarted);
        }
    }
}
