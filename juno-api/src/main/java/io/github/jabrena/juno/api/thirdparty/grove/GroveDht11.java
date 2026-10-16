package io.github.jabrena.juno.api.thirdparty.grove;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;

/**
 * Driver for Seeed's Grove Temperature and Humidity Sensor (DHT11, the blue module with
 * {@code GND}, {@code VCC}, {@code SIG} and {@code NC}). It implements the DHT11 one-wire
 * protocol directly, so no Arduino library or compiler intrinsic is needed.
 *
 * <p>The driver is allocation-free. The caller owns a two-element result buffer: slot
 * {@link #TEMPERATURE_CELSIUS} receives whole degrees Celsius and slot {@link #HUMIDITY_PERCENT}
 * receives relative humidity. A DHT11 conversion is slow; wait at least one second between
 * successful reads. The Grove module's {@code SIG} wire can use any digital Grove socket, for
 * example {@link GroveBaseShield#D4}.
 */
public final class GroveDht11 {
    /** Number of values required by {@link #read(int, int[], int)}. */
    public static final int READING_LENGTH = 2;
    /** Result-buffer slot containing temperature in whole degrees Celsius. */
    public static final int TEMPERATURE_CELSIUS = 0;
    /** Result-buffer slot containing relative humidity in whole percent. */
    public static final int HUMIDITY_PERCENT = 1;

    private static final int RESPONSE_TIMEOUT_MICROS = 100;
    private static final int BIT_ONE_THRESHOLD_MICROS = 50;
    private static final int START_LOW_MILLIS = 20;
    private static final int START_RELEASE_MICROS = 40;

    private GroveDht11() {
    }

    /**
     * Reads one DHT11 frame into {@code reading} without allocating.
     *
     * @param signalPin the digital pin connected to the module's {@code SIG} wire
     * @param reading caller-owned output buffer
     * @param readingLength number of writable slots in {@code reading}; at least {@link #READING_LENGTH}
     * @return {@code true} when the response timing and checksum were valid; {@code false} when
     *     the sensor did not answer or returned a corrupt frame
     * @throws IllegalArgumentException if {@code readingLength} is too small or exceeds the buffer
     */
    public static boolean read(int signalPin, int[] reading, int readingLength) {
        if (readingLength < READING_LENGTH || readingLength > reading.length) {
            throw new IllegalArgumentException("readingLength must be at least 2 and fit reading");
        }

        // The module starts a conversion when the controller holds SIG low, then releases the bus.
        Gpio.pinMode(signalPin, Gpio.OUTPUT);
        Gpio.digitalWrite(signalPin, false);
        Delay.millis(START_LOW_MILLIS);
        Gpio.pinMode(signalPin, Gpio.INPUT_PULLUP);
        Delay.micros(START_RELEASE_MICROS);

        // Sensor response: approximately 80 us low, 80 us high, then a 50 us low bit preamble.
        if (!waitForLevel(signalPin, false) || !waitForLevel(signalPin, true)
                || !waitForLevel(signalPin, false)) {
            return false;
        }

        int humidity = 0;
        int humidityDecimal = 0;
        int temperature = 0;
        int temperatureDecimal = 0;
        int checksum = 0;
        for (int bit = 0; bit < 40; bit++) {
            if (!waitForLevel(signalPin, true)) {
                return false;
            }
            int highStarted = Clock.micros();
            if (!waitForLevel(signalPin, false)) {
                return false;
            }
            int pulseMicros = Clock.micros() - highStarted;
            int mask = 1 << (7 - (bit & 7));
            if (pulseMicros >= BIT_ONE_THRESHOLD_MICROS) {
                switch (bit >>> 3) {
                    case 0 -> humidity |= mask;
                    case 1 -> humidityDecimal |= mask;
                    case 2 -> temperature |= mask;
                    case 3 -> temperatureDecimal |= mask;
                    default -> checksum |= mask;
                }
            }
        }

        if (((humidity + humidityDecimal + temperature + temperatureDecimal) & 0xFF) != checksum) {
            return false;
        }
        reading[TEMPERATURE_CELSIUS] = (temperature & 0x80) == 0 ? temperature : -(temperature & 0x7F);
        reading[HUMIDITY_PERCENT] = humidity;
        return true;
    }

    private static boolean waitForLevel(int pin, boolean high) {
        int started = Clock.micros();
        while (Gpio.digitalRead(pin) != high) {
            if (Clock.micros() - started >= RESPONSE_TIMEOUT_MICROS) {
                return false;
            }
        }
        return true;
    }
}
