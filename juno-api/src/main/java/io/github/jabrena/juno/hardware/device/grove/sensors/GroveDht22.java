package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.robotics.Thermometer;

/**
 * Reads Seeed's <a href="https://wiki.seeedstudio.com/Grove-Temperature_and_Humidity_Sensor_Pro/">
 * Grove Temperature and Humidity Sensor Pro</a> using its DHT22 (AM2302) frame format. The first
 * two bytes contain humidity in tenths of a percent and the next two contain temperature in tenths
 * of a degree Celsius. The caller owns and reuses both buffers; this driver allocates nothing.
 *
 * <p>Allow at least two seconds between readings. A valid frame's checksum alone does not identify
 * the physical sensor, but DHT22-format readings should also be within its measurement range.
 */
public final class GroveDht22 implements Thermometer, SampleProvider {
    public static final int FRAME_LENGTH = 5;
    public static final int READING_LENGTH = 2;
    public static final int TEMPERATURE_TENTHS_CELSIUS = 0;
    public static final int HUMIDITY_TENTHS_PERCENT = 1;

    private static final int NO_PIN = -1;

    private final GroveDhtProtocol protocol;
    private final int boundPin;
    private final int[] boundReading = new int[READING_LENGTH];
    private final int[] boundFrame = new int[FRAME_LENGTH];

    /** A sensor on the board's own pins and timer. Create it once, at startup. */
    public GroveDht22() {
        this(new GroveDhtProtocol(), NO_PIN);
    }

    /** A sensor on {@code gpio} and {@code clock}: mocked or simulated ones in a unit test. */
    public GroveDht22(Gpio gpio, Clock clock) {
        this(new GroveDhtProtocol(gpio, clock), NO_PIN);
    }

    /**
     * A sensor on {@code signalPin} of {@code gpio} and {@code clock}: the form that works as a
     * {@link Thermometer} and a {@link SampleProvider}. Create it once, at startup.
     */
    public GroveDht22(Gpio gpio, Clock clock, int signalPin) {
        this(new GroveDhtProtocol(gpio, clock), signalPin);
    }

    private GroveDht22(GroveDhtProtocol protocol, int boundPin) {
        this.protocol = protocol;
        this.boundPin = boundPin;
    }

    /**
     * Reads one frame using a caller-owned five-int raw frame buffer. Returns {@code false} for
     * incomplete, corrupt or out-of-range frames, leaving {@code reading} unchanged in those cases.
     * The raw frame is still available when all 40 bits arrived but validation failed.
     *
     * @param signalPin digital Grove signal pin
     * @param reading caller-owned temperature/humidity result in tenths
     * @param readingLength writable result slots, at least {@link #READING_LENGTH}
     * @param frame caller-owned raw frame buffer
     * @param frameLength writable frame slots, at least {@link #FRAME_LENGTH}
     * @return whether a plausible, checksum-valid DHT22 frame was read
     */
    public boolean read(int signalPin, int[] reading, int readingLength,
            int[] frame, int frameLength) {
        if (readingLength < READING_LENGTH || readingLength > reading.length) {
            throw new IllegalArgumentException("readingLength must be at least 2 and fit reading");
        }
        int previousTemperature = reading[TEMPERATURE_TENTHS_CELSIUS];
        int previousHumidity = reading[HUMIDITY_TENTHS_PERCENT];
        if (!protocol.read(signalPin, frame, frameLength)) {
            return false;
        }
        if (!decode(frame, frameLength, reading, readingLength)) {
            reading[TEMPERATURE_TENTHS_CELSIUS] = previousTemperature;
            reading[HUMIDITY_TENTHS_PERCENT] = previousHumidity;
            return false;
        }
        return true;
    }

    /**
     * Returns the temperature in degrees Celsius, or {@link Float#NaN} when no valid frame was read.
     *
     * @throws IllegalStateException if the sensor was created without its signal pin
     */
    @Override
    public float getTemperatureCelsius() {
        return readBoundPin() ? boundReading[TEMPERATURE_TENTHS_CELSIUS] / 10f : Float.NaN;
    }

    /** Returns 2: the temperature in degrees Celsius, then the relative humidity from 0 to 1. */
    @Override
    public int sampleSize() {
        return 2;
    }

    /**
     * Writes the temperature in degrees Celsius and the relative humidity from 0 to 1, starting at {@code offset};
     * both are {@link Float#NaN} when no valid frame was read.
     *
     * @throws IllegalStateException if the sensor was created without its signal pin
     */
    @Override
    public void fetchSample(float[] sample, int offset) {
        if (readBoundPin()) {
            sample[offset] = boundReading[TEMPERATURE_TENTHS_CELSIUS] / 10f;
            sample[offset + 1] = boundReading[HUMIDITY_TENTHS_PERCENT] / 1000f;
        } else {
            sample[offset] = Float.NaN;
            sample[offset + 1] = Float.NaN;
        }
    }

    private boolean readBoundPin() {
        if (boundPin == NO_PIN) {
            throw new IllegalStateException("create GroveDht22 with its signal pin to read it as a Thermometer");
        }
        return read(boundPin, boundReading, READING_LENGTH, boundFrame, FRAME_LENGTH);
    }

    boolean decode(int[] frame, int frameLength, int[] reading, int readingLength) {
        if (frameLength < FRAME_LENGTH || frameLength > frame.length
                || readingLength < READING_LENGTH || readingLength > reading.length) {
            throw new IllegalArgumentException("DHT22 frame or reading buffer is too short");
        }
        int humidity = (frame[0] << 8) | frame[1];
        int temperatureMagnitude = ((frame[2] & 0x7F) << 8) | frame[3];
        int temperature = (frame[2] & 0x80) == 0 ? temperatureMagnitude : -temperatureMagnitude;
        int checksum = (frame[0] + frame[1] + frame[2] + frame[3]) & 0xFF;
        if (checksum != frame[4] || humidity > 1000 || temperature < -400 || temperature > 800) {
            return false;
        }
        reading[TEMPERATURE_TENTHS_CELSIUS] = temperature;
        reading[HUMIDITY_TENTHS_PERCENT] = humidity;
        return true;
    }
}
