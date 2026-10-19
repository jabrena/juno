package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.hardware.io.gpio.Gpio;
import io.github.jabrena.juno.api.time.Clock;

/**
 * Reads Seeed's <a href="https://www.seeedstudio.com/Grove-Temperature-Humidity-Sensor-DHT11.html">
 * Grove Temperature &amp; Humidity Sensor (DHT11)</a>. The DHT11 frame stores whole-number
 * humidity and temperature values. The caller owns and reuses both buffers; this driver allocates
 * nothing.
 *
 * <p>Allow at least one second between readings. The monitor in the examples waits two seconds,
 * which also works for DHT22-format modules.
 */
public final class GroveDht11 {
    public static final int FRAME_LENGTH = 5;
    public static final int READING_LENGTH = 2;
    public static final int TEMPERATURE_CELSIUS = 0;
    public static final int HUMIDITY_PERCENT = 1;

    private final GroveDhtProtocol protocol;

    /** A sensor on the board's own pins and timer. Create it once, at startup. */
    public GroveDht11() {
        this.protocol = new GroveDhtProtocol();
    }

    /** A sensor on {@code gpio} and {@code clock}: mocked or simulated ones in a unit test. */
    public GroveDht11(Gpio gpio, Clock clock) {
        this.protocol = new GroveDhtProtocol(gpio, clock);
    }

    /**
     * Reads one checksum-valid DHT11 frame using caller-owned buffers.
     *
     * @param signalPin digital Grove signal pin
     * @param reading caller-owned temperature/humidity result
     * @param readingLength writable result slots, at least {@link #READING_LENGTH}
     * @param frame caller-owned raw frame buffer
     * @param frameLength writable frame slots, at least {@link #FRAME_LENGTH}
     * @return whether a plausible DHT11 frame was read
     */
    public boolean read(int signalPin, int[] reading, int readingLength,
            int[] frame, int frameLength) {
        if (readingLength < READING_LENGTH || readingLength > reading.length) {
            throw new IllegalArgumentException("readingLength must be at least 2 and fit reading");
        }
        int previousTemperature = reading[TEMPERATURE_CELSIUS];
        int previousHumidity = reading[HUMIDITY_PERCENT];
        if (!protocol.read(signalPin, frame, frameLength)) {
            return false;
        }
        if (!decode(frame, frameLength, reading, readingLength)) {
            reading[TEMPERATURE_CELSIUS] = previousTemperature;
            reading[HUMIDITY_PERCENT] = previousHumidity;
            return false;
        }
        return true;
    }

    boolean decode(int[] frame, int frameLength, int[] reading, int readingLength) {
        if (frameLength < FRAME_LENGTH || frameLength > frame.length
                || readingLength < READING_LENGTH || readingLength > reading.length) {
            throw new IllegalArgumentException("DHT11 frame or reading buffer is too short");
        }
        int checksum = (frame[0] + frame[1] + frame[2] + frame[3]) & 0xFF;
        int temperature = (frame[2] & 0x80) == 0 ? frame[2] : -(frame[2] & 0x7F);
        if (checksum != frame[4] || frame[1] != 0 || frame[3] != 0
                || frame[0] > 100 || temperature < 0 || temperature > 50) {
            return false;
        }
        reading[TEMPERATURE_CELSIUS] = temperature;
        reading[HUMIDITY_PERCENT] = frame[0];
        return true;
    }
}
