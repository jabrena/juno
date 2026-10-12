package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.thumb2.AsmEmitter;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.List;

/**
 * Source text of the runtime shim's optional, self-contained C++ helper blocks for peripherals,
 * storage, strings, exceptions and 64-bit/floating-point arithmetic — each appended by
 * {@link RuntimeShim} only when the lowered program actually calls into it.
 */
public final class ShimLibraries {
    private ShimLibraries() {
    }

    /** USB HID mouse bindings; {@code Mouse.h} is an optional library, included only on use. */
    static String mouseHelpers() {
        return RuntimeSource.load("common/mouse.cpp");
    }

    /** One {@code Servo} per digital pin, attached on first use. */
    static String servoHelpers() {
        return RuntimeSource.load("common/servo.cpp");
    }

    /** Register reads and writes on the primary {@code Wire} bus, with the core-bundled {@code Wire} library. */
    static String i2cHelpers() {
        return RuntimeSource.load("common/i2c.cpp");
    }

    /**
     * Framed bytes over 38 kHz infrared, bit-banged on two plain pins: 8 data bits LSB first, odd parity
     * and one stop bit at the configured baud rate, with a software carrier for a 0 bit when transmitting.
     * The receive pin is an IR demodulator module's output, which idles high and goes low in a burst.
     */
    static String infraredHelpers() {
        return RuntimeSource.load("common/infrared.cpp");
    }

    /**
     * A BLE central for one LEGO Powered Up hub, speaking LEGO Wireless Protocol 3.0 through the
     * optional {@code ArduinoBLE} library: every command is one write to the hub's single
     * characteristic, framed as {@code [length, hub id 0, message type, payload...]}, and the hub's
     * notifications on that characteristic carry the sensor values {@code enableSensor} asked for.
     */
    static String legoPoweredUpHelpers(boolean threads) {
        return RuntimeSource.load("common/lego-powered-up.cpp").replace("${JUNO_LEGO_WAIT}", threads
                ? "extern \"C\" void juno_thread_yield();\n"
                        + "                static void juno_lego_wait() {\n"
                        + "                  juno_thread_yield();\n"
                        + "                }"
                : "static void juno_lego_wait() {\n                }");
    }

    /**
     * Read-only {@code java.io.RandomAccessFile}: a handle into the same open-file table as {@code SdCard.open}.
     * Every failure leaves the JDK's own exception pending (the caller's call guard routes it to a handler) and
     * returns a value the program never reads. Needs {@link #sdHelpers()} and {@link #exceptionHelpers} first.
     */
    static String randomAccessFileHelpers(int ioClassId, int fileNotFoundClassId, int eofClassId,
                                          int indexOutOfBoundsClassId) {
        return RuntimeSource.load("common/random-access-file.cpp")
                .replace("${JUNO_RAF_IO}", Integer.toString(ioClassId))
                .replace("${JUNO_RAF_FILE_NOT_FOUND}", Integer.toString(fileNotFoundClassId))
                .replace("${JUNO_RAF_EOF}", Integer.toString(eofClassId))
                .replace("${JUNO_RAF_INDEX_OUT_OF_BOUNDS}", Integer.toString(indexOutOfBoundsClassId))
                .replace("${JUNO_THROWABLE_BYTES}", Integer.toString(ThrowableTypes.HEADER_BYTES))
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / AsmEmitter.WORD))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / AsmEmitter.WORD));
    }

    /** Exposes the arena allocator's current usage and its fixed capacity. */
    static String memoryHelpers() {
        return RuntimeSource.load("common/memory.cpp");
    }

    /** Arduino core {@code random()}/{@code randomSeed()} bindings. */
    static String randomHelpers() {
        return RuntimeSource.load("common/random.cpp");
    }

    /** {@code WiFiS3} station-mode bindings. */
    public static String wifiHelpers() {
        return RuntimeSource.load("common/wifi.cpp");
    }

    /** Arduino SD read/append bindings plus a bounded, arena-backed {@code key=value} parser. */
    public static String sdHelpers() {
        return sdHelpers(4, 16, 32, 64, 96);
    }

    /**
     * {@link #sdHelpers()} with explicit capacities: {@code openFiles} simultaneously open files, and a
     * {@code Properties} of {@code entries} entries of at most {@code keyBytes}/{@code valueBytes} (terminator
     * included), parsed from lines of at most {@code lineBytes}. An 8-bit board with a few KB of RAM uses smaller ones.
     */
    public static String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes, "", "juno_sd.begin(chipSelectPin)");
    }

    /** The SD helpers with a core-specific SPI driver declaration and {@code SdFat32.begin} expression. */
    public static String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes,
                            String spiDeclaration, String beginExpression) {
        return RuntimeSource.load("common/sd.cpp").replace("${JUNO_SD_MAX_OPEN_FILES}", Integer.toString(openFiles))
                .replace("${JUNO_SD_SPI_DECLARATION}", spiDeclaration)
                .replace("${JUNO_SD_BEGIN_EXPRESSION}", beginExpression)
                .replace("${JUNO_PROPERTIES_MAX_ENTRIES}", Integer.toString(entries))
                .replace("${JUNO_PROPERTIES_KEY_CAPACITY}", Integer.toString(keyBytes))
                .replace("${JUNO_PROPERTIES_VALUE_CAPACITY}", Integer.toString(valueBytes))
                .replace("${JUNO_PROPERTIES_LINE_CAPACITY}", Integer.toString(lineBytes));
    }

    /**
     * {@code extern "C"} {@code juno_l*} helpers for 64-bit arithmetic, reused as plain C++ rather
     * than hand-rolled 64-bit assembly, consistent with this backend's existing shim-delegation
     * philosophy for anything nontrivial (see this class's doc).
     */
    static String longHelpers() {
        return RuntimeSource.load("common/long.cpp");
    }

    static String runtimeStringHelpers() {
        return runtimeStringSlots(8) + runtimeStringDoubleFormatting() + runtimeStringOperations()
                + runtimeStringFloatingAppends();
    }

    /**
     * The rotating pool of {@code slots} runtime-string buffers and {@code String.valueOf(int)}: the first part of
     * {@link #runtimeStringHelpers()}, which an architecture without a binary64 {@code double} (AVR) combines with
     * its own floating-point formatting.
     */
    static String runtimeStringSlots(int slots) {
        return RuntimeSource.load("common/runtime-string-slots.cpp").replace("${JUNO_STRING_SLOT_SIZE}", Integer.toString(RuntimeLimits.STRING_SLOT_CAPACITY_BYTES))
                .replace("${JUNO_STRING_SLOT_COUNT}", Integer.toString(slots))
                .replace("${JUNO_STRING_MAX_LENGTH}", Integer.toString(RuntimeLimits.STRING_SLOT_CAPACITY_BYTES - 1));
    }

    /** {@code juno_format_double} and {@code String.valueOf(double)} on the C {@code double}. */
    static String runtimeStringDoubleFormatting() {
        return RuntimeSource.load("thumb2/runtime-string-double-formatting.cpp");
    }

    /** {@code length}, {@code charAt}, {@code equals} and string concatenation of everything but floating point. */
    static String runtimeStringOperations() {
        return RuntimeSource.load("common/runtime-string-operations.cpp");
    }

    /** String concatenation of {@code float} and {@code double}, through {@code juno_format_double}. */
    static String runtimeStringFloatingAppends() {
        return RuntimeSource.load("thumb2/runtime-string-floating-appends.cpp");
    }

    /**
     * {@code StringBuilder} is represented as an arena-allocated header (its {@code length} and
     * {@code capacity}, as two {@code int32_t}s) immediately followed by its {@code capacity}-byte
     * buffer — a single {@code juno_alloc} block, addressed by the header's own pointer (cast to
     * {@code int32_t} the same way every other handle in this file is). {@code toString()} copies
     * the written bytes into the same rotating string-slot pool {@link #runtimeStringHelpers()}
     * uses for every other runtime string, so it needs that pool already declared.
     */
    static String stringBuilderHelpers() {
        return RuntimeSource.load("common/string-builder.cpp");
    }

    /**
     * {@code athrow}'s runtime half: the thrown object's class id selects a handler when the throwing
     * method catches that class ({@code caughtMask} bit set); otherwise it reports the exception the
     * way the JVM does and panics. {@code throw null} reports a {@code NullPointerException}.
     */
    static String exceptionHelpers(List<String> throwableClasses) {
        StringBuilder names = new StringBuilder();
        if (throwableClasses.isEmpty()) {
            names.append("  \"\",\n"); // only `throw null` reaches the runtime; C++ forbids an empty array
        }
        for (String className : throwableClasses) {
            names.append("  \"").append(className.replace('/', '.')).append("\",\n");
        }
        return RuntimeSource.load("common/exceptions.cpp").replace("${JUNO_THROWABLE_NAMES}", names.toString())
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / AsmEmitter.WORD))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / AsmEmitter.WORD));
    }

    /** {@code extern "C"} float helpers, called from assembly with float values in core registers (see {@code JUNO_ASM_ABI}). */
    static String floatHelpers() {
        return RuntimeSource.load("thumb2/float.cpp");
    }

    /** {@code extern "C"} soft-float helpers for {@code double}; see {@link #floatHelpers}. */
    static String doubleHelpers() {
        return RuntimeSource.load("thumb2/double.cpp");
    }
}
