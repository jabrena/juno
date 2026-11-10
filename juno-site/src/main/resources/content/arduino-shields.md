---
title: "Arduino Shields"
description: "The shields and carriers Juno drives, what each one does and which boards it works with."
layout: page
---

A shield plugs onto a board's headers and adds hardware the board does not have. Juno drives each one from plain
Java: most need no Arduino library, and every shield class hands its pins and devices to the program, so a unit test
can pass mocks instead. Seat a shield directly on the board, and check the pins before stacking two.

| Shield | Adds | UNO R3 | UNO WiFi Rev2 | UNO R4 WiFi | UNO Q | Nano ESP32 |
|---|---|:---:|:---:|:---:|:---:|:---:|
| [TFT Touch Shield](#tft-touch-shield) | 2.8" color touch display, microSD | | ✓ | ✓ | ✓ | |
| [Grove Base Shield](#grove-base-shield) | Grove sockets | ✓ | ✓ | ✓ | ✓ | |
| [Arduino 9 Axis Motion Shield](#arduino-9-axis-motion-shield) | BNO055 orientation sensor | ✓ | ✓ | ✓ | ✓ | |
| [LCD Keypad Shield](#lcd-keypad-shield) | 16 × 2 character LCD, five buttons | ✓ | ✓ | ✓ | ✓ | |
| [AZDelivery Data Logger Shield](#azdelivery-data-logger-shield) | microSD, real-time clock | ✓ | ✓ | ✓ | ✓ | |
| [Arduino WiFi Shield](#arduino-wifi-shield) | Wi-Fi for a board without a radio | ✓ | | | | |
| [Nano Connector Carrier](#nano-connector-carrier) | microSD, Grove and Qwiic sockets | | | | | ✓ |

## TFT Touch Shield

<img src="../images/shields/shield-tft.png" alt="ELEGOO 2.8-inch TFT Touch Shield" width="240">

The ELEGOO 2.8" shield: a 240 × 320 ILI9341 color display on an 8-bit parallel bus, a resistive touch panel and a
microSD socket. `TftTouchShield` draws, prints text and reads touches, and `ParallelBus` streams pixels from the
runtime. Juno's games run on it.

This complete example paints a blue screen, then draws a red dot wherever the screen is touched:

```java
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class TftTouchExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield tft = new TftTouchShield();
        tft.fillScreen(TftTouchShield.BLUE);
        tft.setCursor(20, 20);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLUE);
        tft.setTextSize(2);
        tft.println("Touch the screen");

        while (true) {
            if (tft.readTouch()) {
                tft.fillCircle(tft.touchX(), tft.touchY(), 4, TftTouchShield.RED);
            }
            Delay.millis(30);
        }
    }
}
```

[`TouchPaintTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/tft/TouchPaintTFT.java)
is a fuller paint program with a color palette and touch diagnostics.

## Grove Base Shield

<img src="../images/shields/shield-grove.png" alt="Grove Base Shield" width="240">

Seeed Studio's passive adapter: every Grove socket is an ordinary UNO pin, and `GroveBaseShield` names them. Plug a
Grove module in and pass the socket's getter to its driver. See [Grove](../grove-system) for the sensors.

With a Grove DHT22 module in socket D4, this complete example prints temperature and humidity:

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveDht22Example {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveDht22 sensor = new GroveDht22();
        int[] reading = new int[GroveDht22.READING_LENGTH];
        int[] frame = new int[GroveDht22.FRAME_LENGTH];
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (sensor.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
                Serial.print("temperature tenths C=");
                Serial.print(reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS]);
                Serial.print(" humidity tenths %= ");
                Serial.println(reading[GroveDht22.HUMIDITY_TENTHS_PERCENT]);
            } else {
                Serial.println("DHT22 read failed");
            }
            Delay.millis(2000);
        }
    }
}
```

[`GroveDht22Monitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveDht22Monitor.java)
formats the two values as degrees and percent.

## Arduino 9 Axis Motion Shield

<img src="../images/shields/shield-nine-axis.png" alt="Arduino 9 Axis Motion Shield" width="240">

A Bosch BNO055 that fuses its accelerometer and gyroscope and reports the heading in degrees. `NineAxisMotionShield`
reads it over I2C with no Arduino library, which suits measuring how far a vehicle has turned. It holds D7 as the
sensor's reset line.

Constructing the shield initializes it and prints its heading, yaw rate and acceleration:

```java
import io.github.jabrena.juno.hardware.device.imu.NineAxisMotionShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class NineAxisExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        NineAxisMotionShield imu = new NineAxisMotionShield();
        imu.reset();

        while (true) {
            Serial.print("heading=");
            Serial.print(imu.getAngle());
            Serial.print(" yaw=");
            Serial.print((int) imu.getAngularVelocity());
            Serial.print(" ax=");
            Serial.print(imu.getXAccel());
            Serial.print(" ay=");
            Serial.print(imu.getYAccel());
            Serial.print(" az=");
            Serial.println(imu.getZAccel());
            Delay.millis(200);
        }
    }
}
```

[`NineAxisMotionShieldSerial`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/imu/NineAxisMotionShieldSerial.java)
is the same monitor with startup diagnostics.

## LCD Keypad Shield

<img src="../images/shields/shield-lcd.png" alt="LCD Keypad Shield" width="240">

A 16 × 2 character LCD on D4-D9, its backlight on D10, and five buttons read through one analog pin. `LcdKeypadShield`
needs no library.

This complete example displays the currently pressed button on the second row:

```java
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class LcdKeypadExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        LcdKeypadShield lcd = new LcdKeypadShield();
        lcd.print("Press a button");
        int lastButton = -1;

        while (true) {
            int button = lcd.readButton();
            if (button != lastButton) {
                lcd.setCursor(0, 1);
                if (button == LcdKeypadShield.RIGHT) {
                    lcd.print("RIGHT ");
                } else if (button == LcdKeypadShield.UP) {
                    lcd.print("UP    ");
                } else if (button == LcdKeypadShield.DOWN) {
                    lcd.print("DOWN  ");
                } else if (button == LcdKeypadShield.LEFT) {
                    lcd.print("LEFT  ");
                } else if (button == LcdKeypadShield.SELECT) {
                    lcd.print("SELECT");
                } else {
                    lcd.print("NONE  ");
                }
                lastButton = button;
            }
            Delay.millis(50);
        }
    }
}
```

[`LcdKeypadDemo`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/lcd/LcdKeypadDemo.java)
also reports the button code over Serial.

## AZDelivery Data Logger Shield

<img src="../images/shields/shield-data-logger.png" alt="AZDelivery Data Logger Shield" width="240">

A microSD socket on the UNO's SPI pins with chip select D10, plus a DS1307 real-time clock that Juno does not drive.
`DataLoggerShield.getSdCard()` gives the card, which programs use for files and for Wi-Fi credentials kept out of the
source. Its D10 is also the LCD Keypad Shield's backlight.

This complete example creates a log file, appends a line, and reports its size:

```java
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

public final class DataLoggerExample {
    private static final String LOG = "juno-log.txt";

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        SdCard sd = new DataLoggerShield().getSdCard();
        if (!sd.begin()) {
            Serial.println("SD card unavailable");
            return;
        }
        if (sd.appendLine(LOG, "Juno data logger is working")) {
            Serial.print("bytes written=");
            Serial.println(sd.size(LOG));
        } else {
            Serial.println("write failed");
        }
    }
}
```

[`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java)
also reads the file back, deletes it, and supports the Nano Connector Carrier.

## Arduino WiFi Shield

<img src="../images/shields/shield-wifi.png" alt="Arduino WiFi Shield" width="240">

The retired Arduino WiFi Shield gives the UNO R3, which has no radio, Wi-Fi, UDP, TCP and plain HTTP through
`new ArduinoWifiShield().getWifi()`. It reserves D4, D7 and D10, so it cannot be stacked with the LCD Keypad Shield.

This complete example connects the UNO R3 to the network and prints its local address. Set
`JUNO_WIFI_SSID` and `JUNO_WIFI_PASSWORD` before compiling/uploading; do not put credentials in source code:

```java
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class ArduinoWifiShieldExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Wifi wifi = new ArduinoWifiShield().getWifi();
        wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        for (int attempt = 0; wifi.status() != Wifi.STATUS_CONNECTED && attempt < 30; attempt++) {
            Delay.millis(1000);
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("Wi-Fi connection failed");
            return;
        }

        int[] address = new int[4];
        wifi.localIP(address);
        Serial.print("connected at ");
        Serial.print(address[0]);
        Serial.print(".");
        Serial.print(address[1]);
        Serial.print(".");
        Serial.print(address[2]);
        Serial.print(".");
        Serial.println(address[3]);
    }
}
```

[`SntpTime`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTime.java)
continues from the same connection and demonstrates the shield's network clock.

## Nano Connector Carrier

<img src="../images/shields/shield-nano-connector-carrier.png" alt="Arduino Nano Connector Carrier" width="240">

Arduino's passive carrier for the Nano ESP32: a microSD slot, four Grove sockets and a Qwiic connector, all named by
`NanoConnectorCarrier`. See the [Nano ESP32](../arduino-nano-esp32#nano-connector-carrier) page for its pin map and
examples, including remote-controlling a LEGO RCX over infrared.

The carrier is a pin adapter, so a complete program can use a Grove socket directly. Plug a Grove LED into the A0/A1
socket, set the carrier's voltage selector to 3.3 V, and run this blink program:

```java
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class NanoCarrierGroveLed {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(ArduinoNano.getGpio());
        DigitalOutput led = DigitalOutput.of(carrier.getGroveA0());

        while (true) {
            led.high();
            Serial.println("on");
            Delay.millis(500);
            led.low();
            Serial.println("off");
            Delay.millis(500);
        }
    }
}
```

For the carrier's microSD socket, use `carrier.getSdCard()`; it selects D4, not the UNO shields' D10.
