package io.github.jabrena.juno.thirdparty.ir;

import io.github.jabrena.juno.testing.SimulatedTime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only stand-in for the infrared UART intrinsic. Written bytes are recorded in {@link #written}; received bytes
 * come from {@link #replies}, and an empty queue times out, advancing the simulated {@link SimulatedTime} clock by the timeout.
 * Carrier pulses from {@link #mark} and {@link #space} are recorded as positive and negative microseconds.
 */
public final class Infrared {
    public static final int TIMEOUT = -1;
    public static final int FRAMING_ERROR = -2;

    /** Bytes sent with {@link #writeByte} or {@link #echoByte}, in order. */
    public static final List<Integer> written = new ArrayList<>();
    /** Bytes the receiver will return from {@link #readByte}, in order. */
    public static final ArrayDeque<Integer> replies = new ArrayDeque<>();
    /** Carrier pulses: a mark of n microseconds records n, a space records -n. */
    public static final List<Integer> pulses = new ArrayList<>();
    public static boolean receivingFrame;

    private Infrared() {
    }

    public static void reset() {
        written.clear();
        replies.clear();
        pulses.clear();
        receivingFrame = false;
    }

    public static void begin(int receivePin, int transmitPin, int baud) {
    }

    public static boolean receiving() {
        return receivingFrame;
    }

    public static int readByte(int timeoutMicros) {
        if (replies.isEmpty()) {
            SimulatedTime.now += timeoutMicros;
            return TIMEOUT;
        }
        SimulatedTime.now++;
        return replies.poll();
    }

    public static void writeByte(int value) {
        written.add(value);
    }

    public static int echoByte(int value, int latencyMicros) {
        written.add(value);
        return replies.isEmpty() ? TIMEOUT : replies.poll();
    }

    public static void mark(int micros) {
        pulses.add(micros);
    }

    public static void space(int micros) {
        pulses.add(-micros);
    }
}
