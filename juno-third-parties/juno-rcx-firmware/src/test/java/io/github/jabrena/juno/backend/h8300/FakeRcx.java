package io.github.jabrena.juno.backend.h8300;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The RCX boot ROM as {@link H8Simulator} sees it: infrared messages are queued by the test and handed over through
 * {@code check_for_data}/{@code receive_data}, replies are captured from {@code send_data}, and motor, sound, sensor
 * and sleep calls are recorded as text. A sensor read reports the reading the test set, or for a counting input adds
 * its step to the previous value, as rotation counting does. The run stops when the firmware polls for a message and
 * none is left, or when it goes to sleep.
 */
final class FakeRcx implements H8Simulator.Rom {
    private final Deque<int[]> messages = new ArrayDeque<>();
    private final List<int[]> replies = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private final int[][] readings = new int[3][3];
    private int batteryReading = 300;
    private int displayed = -1;
    private int displayStyle;
    private boolean idleElapses;
    private boolean analogReady;
    private int countdownAtMessage = -1;
    private int idlePolls;
    private final Set<Integer> icons = new TreeSet<>();
    private final Set<Integer> everLit = new TreeSet<>();

    /** Queues a message: the opcode and its arguments; the checksum is added as the ROM would receive it. */
    void receive(int... message) {
        int[] withChecksum = new int[message.length + 1];
        int sum = 0;
        for (int i = 0; i < message.length; i++) {
            withChecksum[i] = message[i];
            sum += message[i];
        }
        withChecksum[message.length] = sum & 0xFF;
        messages.add(withChecksum);
    }

    void batteryReading(int reading) {
        batteryReading = reading;
    }

    /** What reading input 0-2 reports: its raw value and converted value, or {@code step} added per read if not 0. */
    void sensorReading(int input, int raw, int value, int step) {
        readings[input] = new int[] {raw, value, step};
    }

    /** Once the messages run out, the auto power-off countdown reaches zero instead of the run ending. */
    void idleElapses() {
        idleElapses = true;
    }

    /** The A/D handler flags fresh readings once, at the first poll for a message. */
    void analogReady() {
        analogReady = true;
    }

    /**
     * Once the messages run out, the firmware polls {@code polls} more times before the run ends, each poll a tenth
     * of a second later on the ROM's firmware timer, so its timed work runs.
     */
    void idlePolls(int polls) {
        idlePolls = polls;
    }

    /** The display icons lit now, as ROM opcodes such as {@code 0x3006}. */
    Set<Integer> icons() {
        return icons;
    }

    /** Every display icon lit at some point of the run. */
    Set<Integer> everLit() {
        return everLit;
    }

    /** Sets the auto power-off countdown to {@code minutes} whenever a message arrives, as time passing would. */
    void countdownAtMessage(int minutes) {
        countdownAtMessage = minutes;
    }

    List<int[]> replies() {
        return replies;
    }

    /**
     * The motor, sound, tone, sensor and sleep calls, as {@code motor 2000 1 7}, {@code sound 2}, {@code tone 440 50},
     * {@code active 1002}, {@code passive 1000}, {@code read 1001} and {@code sleep}.
     */
    List<String> calls() {
        return calls;
    }

    int displayed() {
        return displayed;
    }

    /** The style code of the last number displayed: signed (0x3001) or unsigned (0x301F). */
    int displayStyle() {
        return displayStyle;
    }

    @Override
    public int call(int routine, H8Simulator cpu) {
        int r6 = cpu.register(6);
        switch (routine) {
            case RcxRom.CLEAR_MEMORY -> {
                for (int address = cpu.register(0); address < cpu.register(1); address++) {
                    cpu.writeByte(address, 0);
                }
            }
            case RcxRom.GET_POWER_STATUS -> cpu.writeWord(cpu.stackArgument(0),
                    r6 == RcxRom.POWER_STATUS_BATTERY ? batteryReading : 0x20);
            case RcxRom.CHECK_FOR_DATA -> {
                if (analogReady) {
                    analogReady = false;
                    cpu.writeByte(RcxRemoteFirmware.DISPATCH, 1);
                }
                if (messages.isEmpty() && idlePolls > 0) {
                    idlePolls--;
                    cpu.writeWord(RcxRemoteFirmware.TENTHS, cpu.readWord(RcxRemoteFirmware.TENTHS) + 1);
                    return r6;
                }
                if (messages.isEmpty()) {
                    if (!idleElapses) {
                        throw new H8Simulator.Stop();
                    }
                    idleElapses = false;
                    cpu.writeWord(RcxRemoteFirmware.SHUTOFF_MINUTES, 0);
                    return r6;
                }
                if (countdownAtMessage >= 0) {
                    cpu.writeWord(RcxRemoteFirmware.SHUTOFF_MINUTES, countdownAtMessage);
                }
                cpu.writeByte(r6, 1);
            }
            case RcxRom.RECEIVE_DATA -> {
                int[] message = messages.remove();
                int capacity = cpu.stackArgument(0);
                for (int i = 0; i < Math.min(capacity, message.length); i++) {
                    cpu.writeByte(r6 + i, message[i]);
                }
                int lengthAddress = cpu.stackArgument(1);
                cpu.writeByte(lengthAddress, cpu.readByte(lengthAddress) + message.length);
            }
            case RcxRom.SEND_DATA -> {
                int data = cpu.stackArgument(1);
                int[] reply = new int[cpu.stackArgument(2)];
                for (int i = 0; i < reply.length; i++) {
                    reply[i] = cpu.readByte(data + i);
                }
                replies.add(reply);
                return 0;
            }
            case RcxRom.CONTROL_MOTOR -> calls.add("motor %X %d %d".formatted(r6, cpu.stackArgument(0),
                    cpu.stackArgument(1)));
            case RcxRom.PLAY_SYSTEM_SOUND -> calls.add("sound " + cpu.stackArgument(0));
            case RcxRom.PLAY_SOUND_OR_SET_DATA_POINTER -> calls.add("tone %d %d".formatted(cpu.stackArgument(0),
                    cpu.stackArgument(1)));
            case RcxRom.SET_LCD_NUMBER -> {
                displayStyle = r6;
                displayed = cpu.stackArgument(0);
            }
            case RcxRom.SET_LCD_SEGMENT -> {
                icons.add(r6);
                everLit.add(r6);
            }
            case RcxRom.CLEAR_LCD_SEGMENT -> icons.remove(r6);
            case RcxRom.SET_SENSOR_ACTIVE -> calls.add("active %X".formatted(r6));
            case RcxRom.SET_SENSOR_PASSIVE -> calls.add("passive %X".formatted(r6));
            case RcxRom.READ_SENSOR -> {
                calls.add("read %X".formatted(r6));
                int[] reading = readings[r6 - RcxRom.SENSOR_1];
                int sensor = cpu.stackArgument(0);
                int value = reading[2] != 0 ? cpu.readWord(sensor + 4) + reading[2] : reading[1];
                cpu.writeWord(sensor + 2, reading[0]);
                cpu.writeWord(sensor + 4, value);
                cpu.writeByte(sensor + 6, value != 0 ? 1 : 0);
            }
            case RcxRom.SHUTDOWN_POWER -> {
                calls.add("sleep");
                throw new H8Simulator.Stop();
            }
            default -> {
            }
        }
        return r6;
    }
}
