package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * The JLM1 neural terminal in Java: the Nano ESP32 opens its own Wi-Fi network {@code JLM1-NEURAL} (password
 * {@code tinyterminal}) and serves the {@link LanguageModelWebChat} page at {@code http://192.168.4.1}. No internet and no
 * credentials are needed: join the board's network and chat with the model running on it.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class LanguageModelAccessPointChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final String SSID = "JLM1-NEURAL";
    private static final String PASSWORD = "tinyterminal";
    private static final int HTTP_PORT = 80;
    private static final int[] OCTETS = new int[4];

    private LanguageModelAccessPointChat() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("JLM1: loading JUNO.BIN into PSRAM");
        Serial.print("JLM1: model load status ");
        Serial.println(LanguageModelStatus.describe(MODEL.begin()));

        Wifi wifi = MCU.getWifi();
        wifi.beginAP(SSID, PASSWORD);
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        LanguageModelWebChat.register(server);
        server.start();
        while (true) {
            Serial.print("JLM1: join Wi-Fi ");
            Serial.print(SSID);
            Serial.print(" and open http://");
            printAddress(wifi);
            Serial.print("JLM1: model ");
            Serial.println(MODEL.isLoaded() ? "online" : "offline");
            Delay.millis(10000);
        }
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
