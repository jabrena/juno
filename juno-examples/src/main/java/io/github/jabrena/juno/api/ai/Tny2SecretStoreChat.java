package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Chat with the on-board TNY2 model from any device on your network: the Nano ESP32 joins the Wi-Fi network whose
 * {@code wifi.ssid} and {@code wifi.password} are in the board's Secret Store, then serves the {@link Tny2WebChat}
 * page on port 80. Open the address it prints every 10 seconds on the serial port. The model still runs on the
 * board; the network only carries the page and the answers.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class Tny2SecretStoreChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final int HTTP_PORT = 80;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int[] OCTETS = new int[4];

    private Tny2SecretStoreChat() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("TINY: loading JUNO.BIN into PSRAM");
        Serial.print("TINY: model load status ");
        Serial.println(Tny2ModelLoader.load(MODEL));

        Wifi wifi = MCU.getWifi();
        if (!joinWifi(wifi, MCU.getSecretStore())) {
            while (true) {
                Serial.println("TINY: Wi-Fi unavailable; store wifi.ssid and wifi.password in the Secret Store");
                Delay.millis(10000);
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        Tny2WebChat.register(server);
        server.start();
        while (true) {
            Serial.print("TINY: open http://");
            printAddress(wifi);
            Serial.print("TINY: model ");
            Serial.println(MODEL.isLoaded() ? "online" : "offline");
            Delay.millis(10000);
        }
    }

    /** Joins the network named in the Secret Store; {@code false} when a credential is missing or joining fails. */
    private static boolean joinWifi(Wifi wifi, SecretStore secrets) {
        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("TINY: wifi.ssid or wifi.password is missing from the Secret Store");
            return false;
        }
        wifi.begin(ssid, password);
        for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS && wifi.status() != Wifi.STATUS_CONNECTED; attempt++) {
            Serial.print("TINY: Wi-Fi status ");
            Serial.println(wifi.status());
            Delay.millis(1000);
        }
        return wifi.status() == Wifi.STATUS_CONNECTED;
    }

    private static void printAddress(Wifi wifi) {
        wifi.localIP(OCTETS);
        Serial.print(OCTETS[0]);
        Serial.print(".");
        Serial.print(OCTETS[1]);
        Serial.print(".");
        Serial.print(OCTETS[2]);
        Serial.print(".");
        Serial.println(OCTETS[3]);
    }
}
