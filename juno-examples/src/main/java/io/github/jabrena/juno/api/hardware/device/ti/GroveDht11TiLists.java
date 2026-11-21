package io.github.jabrena.juno.api.hardware.device.ti;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht11;
import io.github.jabrena.juno.hardware.device.ti.TiLink;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.time.LocalDateTime;

/**
 * Logs a Grove DHT11 into a TI-84 Plus CE: every five seconds the board reads the sensor and sends the whole run so far
 * to the calculator as three lists, one row per reading: {@code L1} the UTC date and time as {@code YYYYMMDDHHMMSS}
 * (fourteen digits, the calculator's full precision), {@code L2} the temperature in degrees Celsius and {@code L3} the
 * relative humidity in percent. After {@link TiLink#MAX_LIST_LENGTH} readings the oldest is dropped.
 *
 * <p>The time comes from {@link LocalDateTime#now()} over SNTP, so the board joins Wi-Fi first with {@code wifi.ssid}
 * and {@code wifi.password} from its secret store (provisioned once with {@code juno:secret}). The calculator plugs into
 * the USB Host Shield with its ordinary computer cable, as with TI Connect, and should sit at the home screen; the
 * Grove Base Shield on top carries the DHT11 on D4.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.ti.GroveDht11TiLists -Djuno.board=arduino-uno-r4-wifi
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR4WiFiBoard.class})
public final class GroveDht11TiLists {
    private static final int PERIOD_MILLIS = 5000;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int TIME_LIST = 1;
    private static final int TEMPERATURE_LIST = 2;
    private static final int HUMIDITY_LIST = 3;

    private GroveDht11TiLists() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1500);
        Serial.println("Grove DHT11 -> TI-84 Plus CE lists L1 (UTC YYYYMMDDHHMMSS), L2 (C), L3 (%)");
        if (!connectWifi()) {
            return;
        }

        GroveBaseShield grove = new GroveBaseShield(MCU.getGpio());
        GroveDht11 dht11 = new GroveDht11(grove.getD4());
        int[] reading = new int[GroveDht11.READING_LENGTH];
        int[] frame = new int[GroveDht11.FRAME_LENGTH];
        TiLink calculator = new TiLink();
        long[] stamps = new long[TiLink.MAX_LIST_LENGTH];
        int[] celsius = new int[TiLink.MAX_LIST_LENGTH];
        int[] humidity = new int[TiLink.MAX_LIST_LENGTH];
        int count = 0;

        while (true) {
            LocalDateTime now = LocalDateTime.now();
            if (now.equals(LocalDateTime.MIN)) {
                Serial.println("waiting for SNTP time");
            } else if (!dht11.read(reading, reading.length, frame, frame.length)) {
                Serial.println("DHT11 read failed");
            } else {
                if (count == TiLink.MAX_LIST_LENGTH) {
                    for (int i = 1; i < count; i++) {
                        stamps[i - 1] = stamps[i];
                        celsius[i - 1] = celsius[i];
                        humidity[i - 1] = humidity[i];
                    }
                    count--;
                }
                stamps[count] = stamp(now);
                celsius[count] = reading[GroveDht11.TEMPERATURE_CELSIUS];
                humidity[count] = reading[GroveDht11.HUMIDITY_PERCENT];
                count++;
                send(calculator, stamps, celsius, humidity, count);
            }
            Delay.millis(PERIOD_MILLIS);
        }
    }

    /** The date and time as one fourteen-digit number, {@code YYYYMMDDHHMMSS}. */
    private static long stamp(LocalDateTime time) {
        long date = time.getYear() * 10_000L + time.getMonthValue() * 100L + time.getDayOfMonth();
        return date * 1_000_000L + time.getHour() * 10_000L + time.getMinute() * 100L + time.getSecond();
    }

    private static boolean connectWifi() {
        Wifi wifi = MCU.getWifi();
        String ssid = MCU.getSecretStore().getString("wifi.ssid");
        String password = MCU.getSecretStore().getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("wifi.ssid or wifi.password missing from the secret store: run juno:secret");
            return false;
        }
        wifi.begin(ssid, password);
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.print("Wi-Fi connection failed; status=");
            Serial.println(wifi.status());
            return false;
        }
        Serial.println("Wi-Fi connected");
        return true;
    }

    private static void send(TiLink calculator, long[] stamps, int[] celsius, int[] humidity, int count) {
        if (!calculator.isConnected()) {
            if (!calculator.connect()) {
                report("connect", calculator);
                return;
            }
            Serial.print("calculator connected, raw buffer ");
            Serial.println(calculator.bufferSize());
        }
        if (calculator.sendList(TIME_LIST, stamps, count, 0)
                && calculator.sendList(TEMPERATURE_LIST, celsius, count, 0)
                && calculator.sendList(HUMIDITY_LIST, humidity, count, 0)) {
            Serial.print("sent ");
            Serial.print(count);
            Serial.print(" rows, last ");
            Serial.print(stamps[count - 1]);
            Serial.print(" ");
            Serial.print(celsius[count - 1]);
            Serial.print(" C ");
            Serial.print(humidity[count - 1]);
            Serial.println(" %");
        } else {
            report("send", calculator);
        }
    }

    private static void report(String step, TiLink calculator) {
        Serial.print(step);
        Serial.print(" failed: error ");
        Serial.print(calculator.lastError());
        Serial.print(", packet type ");
        Serial.print(calculator.lastPacketType());
        Serial.print(", calculator error ");
        Serial.println(calculator.lastCalculatorError());
    }
}
