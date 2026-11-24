package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * The web chat of {@link LanguageModelSecretStoreChat} with the model from the Nano Connector Carrier's microSD card
 * instead of the bundled one, so a newly trained {@code JUNO.BIN} only needs copying onto the card: the Nano ESP32
 * loads {@code /JUNO.BIN} into PSRAM, joins the Wi-Fi network whose {@code wifi.ssid} and {@code wifi.password} are in
 * its Secret Store, and serves the {@link LanguageModelWebChat} page on port 80. Open the address it prints every 10
 * seconds on the serial port.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class LanguageModelSdCardSecretStoreChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final String MODEL_FILE = "/JUNO.BIN";
    private static final int HTTP_PORT = 80;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int RETRY_MS = 10000;
    private static final int[] OCTETS = new int[4];

    private LanguageModelSdCardSecretStoreChat() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        SdCard card = new NanoConnectorCarrier(MCU.getGpio()).getSdCard();
        while (!card.begin()) {
            Serial.println("JLM1: no microSD card in the Nano Connector Carrier");
            Delay.millis(RETRY_MS);
        }
        Serial.print("JLM1: loading ");
        Serial.print(MODEL_FILE);
        Serial.println(" from the microSD card into PSRAM");
        ModelStatus status = MODEL.begin(card, MODEL_FILE);
        Serial.print("JLM1: model load status ");
        Serial.println(LanguageModelStatus.describe(status));

        Wifi wifi = MCU.getWifi();
        if (!joinWifi(wifi, MCU.getSecretStore())) {
            while (true) {
                Serial.println("JLM1: Wi-Fi unavailable; store wifi.ssid and wifi.password in the Secret Store");
                Delay.millis(RETRY_MS);
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        LanguageModelWebChat.register(server);
        server.start();
        while (true) {
            Serial.print("JLM1: open http://");
            printAddress(wifi);
            Serial.print("JLM1: model from ");
            Serial.print(MODEL_FILE);
            Serial.println(MODEL.isLoaded() ? " online" : " offline");
            Delay.millis(RETRY_MS);
        }
    }

    /** Joins the network named in the Secret Store; {@code false} when a credential is missing or joining fails. */
    private static boolean joinWifi(Wifi wifi, SecretStore secrets) {
        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("JLM1: wifi.ssid or wifi.password is missing from the Secret Store");
            return false;
        }
        wifi.begin(ssid, password);
        for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS && wifi.status() != Wifi.STATUS_CONNECTED; attempt++) {
            Serial.print("JLM1: Wi-Fi status ");
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
