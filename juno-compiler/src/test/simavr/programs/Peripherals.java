package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.email.Pop3Client;
import io.github.jabrena.juno.hardware.net.email.Smtp;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The UNO WiFi Rev2's peripheral APIs end to end on the 8-bit target, against the simavr harness's stand-ins: Wi-Fi
 * and a WiFiClient/WiFiSSLClient that answers one chunked HTTP response, or plays an SMTP or POP3 server (WiFiNINA.h), a loopback UDP socket (WiFiUdp.h), an SD card
 * in RAM holding config.txt (SdFat.h), and pins that read as zero for the LCD and TFT shields. No JVM can run these
 * APIs, so Peripherals.expected holds the output by hand.
 */
@Board(ArduinoUnoR3WiFiRev2.class)
public final class Peripherals {
    static void printBytes(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            Serial.print((char) bytes[i] + "");
        }
    }

    public static void main(String[] args) throws IOException {
        Wifi.begin("juno", "secret");
        Serial.println(Wifi.status() == Wifi.STATUS_CONNECTED ? "wifi up" : "wifi down");
        int[] ip = new int[4];
        Wifi.localIP(ip);
        Serial.println(ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3]);

        byte[] body = new byte[64];
        byte[] headers = new byte[96];
        int[] status = new int[2];
        int length = HttpClient.post("http://example.com/readings", "{\"t\":1}", body, body.length, headers,
                headers.length, status);
        Serial.println(status[0]);
        Serial.println(length);
        Serial.println(Json.getInt(body, length, "n"));
        Serial.println(Json.getBool(body, length, "ok") ? "ok" : "not ok");
        double temp = Json.getDouble(body, length, "temp");
        Serial.println((int) (temp * 10));
        Serial.println("temp=" + temp);
        int secure = HttpClient.get("https://example.com/readings", body, body.length, headers, headers.length,
                status);
        Serial.println(status[0] + " " + secure + " " + Json.getInt(body, secure, "n"));
        Serial.println(HttpClient.get("https://example.com:80/", body, body.length, headers, headers.length, status));

        Serial.println(Smtp.sendTls("smtp.example.com", 465, "rev2@example.com", "secret", "rev2@example.com",
                "you@example.com", "Hello", "Sent from a UNO WiFi Rev2"));
        Serial.println(Smtp.sendTls("smtp.example.com", 465, "rev2@example.com", "wrong", "rev2@example.com",
                "you@example.com", "Hello", "Sent from a UNO WiFi Rev2"));
        Serial.println(Pop3Client.messageCount("pop.example.com", 995, "rev2", "secret"));
        Serial.println(Pop3Client.messageCount("pop.example.com", 995, "rev2", "wrong"));
        int mailBody = Pop3Client.readLatest("pop.example.com", 995, "rev2", "secret", headers, headers.length, body,
                body.length, status);
        printBytes(headers, status[0]);
        printBytes(body, mailBody);
        int subject = Pop3Client.readSubject("pop.example.com", 995, "rev2", "secret", 1, body, body.length);
        printBytes(body, subject);
        Serial.println("");
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

        LcdKeypadShield lcd = new LcdKeypadShield();
        lcd.begin();
        lcd.print("Juno on Rev2");
        Serial.println(lcd.readButton());

        TftTouchShield.begin();
        TftTouchShield.fillRect(0, 0, 8, 8, TftTouchShield.GREEN);
        TftTouchShield.println("Rev2");
        Serial.println(TftTouchShield.readTouch() ? "touched" : "untouched");
        Serial.println("done");
    }
}
