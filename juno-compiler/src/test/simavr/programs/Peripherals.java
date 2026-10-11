package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoWiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.net.http.HttpClient;
import io.github.jabrena.juno.api.net.http.Json;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The UNO WiFi Rev2's peripheral APIs end to end on the 8-bit target, against the simavr harness's stand-ins: Wi-Fi
 * and a WiFiClient that answers one chunked HTTP response (WiFiNINA.h), a loopback UDP socket (WiFiUdp.h), an SD card
 * in RAM holding config.txt (SdFat.h), and pins that read as zero for the LCD and TFT shields. No JVM can run these
 * APIs, so Peripherals.expected holds the output by hand.
 */
@Board(ArduinoUnoWiFiRev2.class)
public final class Peripherals {
    public static void main(String[] args) throws IOException {
        Wifi.begin("juno", "secret");
        Serial.println(Wifi.status() == Wifi.STATUS_CONNECTED ? "wifi up" : "wifi down");
        int[] ip = new int[4];
        Wifi.localIP(ip);
        Serial.println(ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3]);

        byte[] body = new byte[64];
        byte[] headers = new byte[96];
        int[] status = new int[2];
        int length = HttpClient.post("example.com", 80, "/readings", "{\"t\":1}", body, body.length, headers,
                headers.length, status);
        Serial.println(status[0]);
        Serial.println(length);
        Serial.println(Json.getInt(body, length, "n"));
        Serial.println(Json.getBool(body, length, "ok") ? "ok" : "not ok");
        double temp = Json.getDouble(body, length, "temp");
        Serial.println((int) (temp * 10));
        Serial.println("temp=" + temp);

        Serial.println(Udp.listen(4210) ? "listening" : "no socket");
        byte[] payload = new byte[4];
        payload[0] = 'p';
        payload[1] = 'i';
        payload[2] = 'n';
        payload[3] = 'g';
        int[] peer = new int[Udp.ENDPOINT_SIZE];
        peer[0] = 10;
        peer[3] = 7;
        peer[Udp.PORT] = 4210;
        Serial.println(Udp.send(peer, peer[Udp.PORT], payload, payload.length));
        byte[] received = new byte[8];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        int count = Udp.receive(received, received.length, source);
        Serial.println(count);
        Serial.println(source[0] + "." + source[3] + ":" + source[Udp.PORT]);
        Serial.println((char) received[0] + "" + (char) received[3]);
        Udp.stop();

        Serial.println(SdCard.begin() ? "sd ready" : "sd failed");
        Serial.println(SdCard.size("config.txt"));
        Properties config = new Properties();
        InputStream file = SdCard.open("config.txt");
        config.load(file);
        file.close();
        Serial.println(config.getProperty("ssid"));
        Serial.println(config.getProperty("port"));
        Serial.println(config.getProperty("name"));
        Serial.println(config.getProperty("missing", "fallback"));
        SdCard.remove("log.txt");
        SdCard.appendLine("log.txt", "first");
        SdCard.appendText("log.txt", "x=" + 42);
        Serial.println(SdCard.size("log.txt"));
        Serial.println(SdCard.exists("log.txt") ? "log exists" : "no log");
        InputStream log = SdCard.open("log.txt");
        StringBuilder line = new StringBuilder(16);
        while (log.available() > 0) {
            line.append((char) log.read());
        }
        log.close();
        Serial.println(line.toString());

        LcdKeypadShield.begin();
        LcdKeypadShield.print("Juno on Rev2");
        Serial.println(LcdKeypadShield.readButton());

        TftTouchShield.begin();
        TftTouchShield.fillRect(0, 0, 8, 8, TftTouchShield.GREEN);
        TftTouchShield.println("Rev2");
        Serial.println(TftTouchShield.readTouch() ? "touched" : "untouched");
        Serial.println("done");
    }
}
