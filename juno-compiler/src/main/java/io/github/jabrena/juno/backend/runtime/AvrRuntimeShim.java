package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.intrinsic.Intrinsic;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generates the C++ runtime shim of an AVR program, the counterpart of {@link RuntimeShim}. It reuses every shared
 * helper block whose C is width-independent — the arena and its conservative collector, runtime strings, string
 * builders, exceptions, the SD card and {@code Properties}, UDP, the HTTP/1.1 codec, atomics — and
 * replaces the parts that assume a 32-bit {@code int} or a binary64 {@code double}: AVR's {@code int} is 16 bits and
 * its {@code double} is binary32, so Java's {@code float}/{@code double} run on {@link SoftFloatRuntime} and every
 * Arduino core function is reached through a wrapper taking Java-width parameters. The generated assembly calls all
 * of it through the thunks {@link AvrShimThunks} appends, built from the prototypes in this very source.
 *
 * <p>RAM is the scarce resource (the ATmega4809 has 6 KB), so the pools are smaller than on the 32-bit boards: four
 * runtime-string slots, two open SD files and {@code Properties} of eight short entries.
 */
public final class AvrRuntimeShim {
    private final SoftFloatRuntime softFloatRuntime = new SoftFloatRuntime();
    private final ScopedValueRuntime scopedValueRuntime = new ScopedValueRuntime();
    private final AtomicRuntime atomicRuntime = new AtomicRuntime();
    private final AvrShimThunks avrShimThunks = new AvrShimThunks();
    private final MathRuntime mathRuntime = new MathRuntime();
    private final BigNumberRuntime bigNumberRuntime = new BigNumberRuntime();
    private final UuidRuntime uuidRuntime = new UuidRuntime();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();
    private final IoRuntime ioRuntime = new IoRuntime();
    private final NetworkShimLibraries networkShimLibraries = new NetworkShimLibraries();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final AllocatorRuntime allocatorRuntime = new AllocatorRuntime();

    /** Runtime-string slots; each is {@code RuntimeLimits.STRING_SLOT_CAPACITY_BYTES} bytes. */
    static final int STRING_SLOTS = 4;
    static final int SD_OPEN_FILES = 2;
    static final int PROPERTIES_ENTRIES = 8;
    static final int PROPERTIES_KEY_BYTES = 16;
    /** A WPA/WPA2 passphrase may contain 63 characters, plus the terminating null byte. */
    static final int PROPERTIES_VALUE_BYTES = 64;
    /** Holds {@code wifi.password=} plus a maximum-length passphrase and its terminator. */
    static final int PROPERTIES_LINE_BYTES = 96;

    /** Shim features this board's runtime does not have; the linker's capability checks normally stop them first. */
    private static final Set<ShimFeature> UNSUPPORTED = EnumSet.of(ShimFeature.LED_MATRIX, ShimFeature.MOUSE,
            ShimFeature.LEGO_POWERED_UP, ShimFeature.HTTP_SERVER, ShimFeature.SMTP);

    private final CoreRuntime core;
    private final RuntimeConfig config;
    private final boolean gcLoggingEnabled;
    private final Set<ShimFeature> features;
    private final Set<Intrinsic> usedMath;
    private final List<String> throwableClasses;
    private final Set<String> thunkFunctions;
    private final AvrWatchdogRuntime watchdogRuntime;

    /**
     * @param thunkFunctions every shim function the assembly calls through a thunk
     * @param argumentWords the size of {@code juno_avr_args}, in words (the assembly defines it)
     */
    public AvrRuntimeShim(CoreRuntime core, RuntimeConfig config, boolean gcLoggingEnabled, Set<ShimFeature> features,
                   Set<Intrinsic> usedMath, List<String> throwableClasses, Set<String> thunkFunctions,
                   int argumentWords, Optional<Integer> watchdogTimeoutMillis) {
        defineFloatingMath();
        this.core = core;
        this.config = config;
        this.gcLoggingEnabled = gcLoggingEnabled;
        this.features = features;
        this.usedMath = usedMath;
        this.throwableClasses = throwableClasses;
        this.thunkFunctions = thunkFunctions;
        this.watchdogRuntime = new AvrWatchdogRuntime(core, watchdogTimeoutMillis);
        if (argumentWords > 255) {
            throw Architecture.AVR.unsupported("a call with more than 255 argument words");
        }
    }

    public String generate() {
        for (ShimFeature feature : features) {
            if (UNSUPPORTED.contains(feature)) {
                throw Architecture.AVR.unsupported("the " + feature + " runtime");
            }
        }
        StringBuilder shim = new StringBuilder(runtimeSource.load("avr/shim-header.cpp"));
        appendIncludes(shim);
        shim.append(avrShimThunks.prelude());
        shim.append(softFloatRuntime.helpers());
        shim.append(coreRuntime());
        appendHelpers(shim);
        String source = shim.toString();
        return source + avrShimThunks.generate(source, thunkFunctions);
    }

    private boolean uses(ShimFeature feature) {
        return features.contains(feature);
    }

    private boolean usesAny(ShimFeature... candidates) {
        for (ShimFeature candidate : candidates) {
            if (features.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void appendIncludes(StringBuilder shim) {
        if (uses(ShimFeature.SERVO)) {
            shim.append("#include <Servo.h>\n");
        }
        if (uses(ShimFeature.I2C)) {
            shim.append("#include <Wire.h>\n");
        }
        if (usesAny(ShimFeature.WIFI, ShimFeature.UDP, ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.SMTP_TLS,
                ShimFeature.POP3)) {
            shim.append(core.wifiIncludes(uses(ShimFeature.UDP)));
        }
        if (usesAny(ShimFeature.HTTPS, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.httpsInclude());
        }
        if (uses(ShimFeature.SD)) {
            shim.append("#include <SPI.h>\n#include <SdFat.h>\n");
        }
        shim.append(watchdogRuntime.includes());
    }

    /** Panic, the arena and collector, the integer and core-function helpers, and Serial. */
    private String coreRuntime() {
        // With threads every live thread's stack is a root, not just the one running (see ThreadRuntime).
        String gcStackScan = uses(ShimFeature.THREADS)
                ? "juno_thread_gc_scan(&stackMarker);"
                : "juno_gc_scan_bytes(&stackMarker, reinterpret_cast<const uint8_t*>(juno_gc_stack_top));";
        String gcDeclarations = "static void juno_gc_scan_bytes(const uint8_t* from, const uint8_t* to);\n"
                + (uses(ShimFeature.THREADS) ? "static void juno_thread_gc_scan(const uint8_t* marker);\n" : "");
        return """

                typedef uint32_t juno_f32;

                static inline juno_f64 juno_f32_wide(juno_f32 value) { return juno_f64_from_f32(value); }

                extern "C" [[noreturn]] void juno_panic() {
                ${JUNO_WATCHDOG_PANIC_MESSAGE}
                  noInterrupts();
                  for (;;) {}
                }
                """
                .replace("${JUNO_WATCHDOG_PANIC_MESSAGE}", watchdogRuntime.panicMessage())
                + watchdogRuntime.functions()
                + allocatorRuntime.source(config.arenaBytes(), 2, gcLoggingEnabled, gcStackScan, gcDeclarations)
                + runtimeSource.load("avr/core-runtime.cpp")
                .replace("${PIN_MODE_TYPE}", core.avrPinModeType())
                .replace("${PIN_STATUS_TYPE}", core.avrPinStatusType())
                .replace("${OVERFLOW}", f64(4294967040.0))
                .replace("${NEGATIVE_OVERFLOW}", f64(-4294967040.0))
                .replace("${HALF}", f64(0.5))
                .replace("${TEN}", f64(10.0));
    }

    /** A binary64 constant as its bit pattern in a C++ literal. */
    private String f64(double value) {
        return "0x" + Long.toHexString(Double.doubleToRawLongBits(value)).toUpperCase(java.util.Locale.ROOT) + "ull";
    }

    private void appendHelpers(StringBuilder shim) {
        if (usesAny(ShimFeature.RUNTIME_STRINGS, ShimFeature.STRING_BUILDER)) {
            shim.append(shimLibraries.runtimeStringSlots(STRING_SLOTS))
                    .append(doubleFormatting())
                    .append(shimLibraries.runtimeStringOperations())
                    .append(floatingAppends());
        }
        if (uses(ShimFeature.STRING_BUILDER)) {
            shim.append(shimLibraries.stringBuilderHelpers());
        }
        if (uses(ShimFeature.SERVO)) {
            shim.append(shimLibraries.servoHelpers());
        }
        if (uses(ShimFeature.PARALLEL_BUS)) {
            shim.append(core.parallelBusHelpers());
        }
        if (uses(ShimFeature.INFRARED)) {
            shim.append(shimLibraries.infraredHelpers());
        }
        if (uses(ShimFeature.I2C)) {
            shim.append(shimLibraries.i2cHelpers());
        }
        if (uses(ShimFeature.MEMORY)) {
            shim.append(shimLibraries.memoryHelpers());
        }
        if (uses(ShimFeature.FLASH)) {
            shim.append(core.flashHelpers());
        }
        if (uses(ShimFeature.RANDOM)) {
            shim.append(shimLibraries.randomHelpers());
        }
        shim.append(ioRuntime.preludeFor(core, features));
        if (uses(ShimFeature.SD)) {
            shim.append(core.sdHelpers(SD_OPEN_FILES, PROPERTIES_ENTRIES, PROPERTIES_KEY_BYTES,
                    PROPERTIES_VALUE_BYTES, PROPERTIES_LINE_BYTES));
        }
        if (uses(ShimFeature.SD_CAPACITY)) {
            shim.append(shimLibraries.sdCapacityHelpers());
        }
        if (uses(ShimFeature.LONG)) {
            shim.append(shimLibraries.longHelpers());
        }
        if (uses(ShimFeature.FLOAT)) {
            shim.append(floatHelpers());
        }
        if (uses(ShimFeature.DOUBLE)) {
            shim.append(doubleHelpers());
        }
        if (!usedMath.isEmpty()) {
            shim.append(mathHelpers());
        }
        if (uses(ShimFeature.EXCEPTIONS)) {
            shim.append(shimLibraries.exceptionHelpers(throwableClasses));
        }
        if (uses(ShimFeature.RANDOM_ACCESS_FILE)) {
            shim.append(shimLibraries.randomAccessFileHelpers(
                    throwableClasses.indexOf("java/io/IOException"),
                    throwableClasses.indexOf("java/io/FileNotFoundException"),
                    throwableClasses.indexOf("java/io/EOFException"),
                    throwableClasses.indexOf("java/lang/IndexOutOfBoundsException")));
        }
        appendNetworkHelpers(shim);
        if (uses(ShimFeature.ATOMICS)) {
            shim.append(atomicRuntime.helpers());
        }
        if (uses(ShimFeature.UUID_RANDOM)) {
            shim.append(core.uuidRandomHelpers());
        }
        if (uses(ShimFeature.UUID)) {
            shim.append(uuidRuntime.helpers(throwableClasses.indexOf("java/lang/IllegalArgumentException"),
                    uses(ShimFeature.UUID_RANDOM)));
        }
        if (uses(ShimFeature.BIG_NUMBERS)) {
            shim.append(bigNumberRuntime.handleHelpers())
                    .append(bigNumberRuntime.helpersWithoutDoubles(
                            throwableClasses.indexOf("java/lang/ArithmeticException"),
                            throwableClasses.indexOf("java/lang/NumberFormatException"),
                            throwableClasses.indexOf("java/lang/IllegalArgumentException")))
                    .append(bigNumberDoubles(uses(ShimFeature.BIG_DECIMAL_DOUBLE)));
        }
        appendConcurrencyHelpers(shim);
    }

    /** Scoped-value bindings, then the thread scheduler that swaps them (it refers to the binding stack). */
    private void appendConcurrencyHelpers(StringBuilder shim) {
        if (uses(ShimFeature.SCOPED_VALUES)) {
            shim.append(scopedValueRuntime.bindings(uses(ShimFeature.THREAD_ENTRY),
                    uses(ShimFeature.SCOPED_CALL_ENTRY),
                    throwableClasses.indexOf("java/util/NoSuchElementException")));
        }
        if (uses(ShimFeature.THREADS)) {
            shim.append(core.threadPort(config));
            shim.append(ioRuntime.workerPortFor(core, features));
            shim.append(threadRuntime.scheduler(false, ioRuntime.usesWorker(core, features),
                    core.delayMillisFunction(), "juno_gc_scan_bytes",
                    uses(ShimFeature.EXCEPTIONS), uses(ShimFeature.THREAD_ENTRY),
                    uses(ShimFeature.TASK_CALLABLE_ENTRY), uses(ShimFeature.STRUCTURED_TASKS),
                    uses(ShimFeature.SCOPED_VALUES),
                    throwableClasses.indexOf("java/util/concurrent/ExecutionException"),
                    throwableClasses.indexOf("java/lang/IllegalStateException")));
        } else if (uses(ShimFeature.THREAD_BASICS)) {
            shim.append(threadRuntime.basics(core.delayMillisFunction()));
        }
    }

    /**
     * {@code BigInteger.doubleValue}, {@code BigDecimal.doubleValue} and {@code BigDecimal.valueOf(double)} on
     * binary64. The JDK rounds both {@code doubleValue}s correctly (to nearest, ties to even), as the 32-bit boards'
     * {@code strtod} does, so these take the value's limbs straight to binary64: a BigDecimal with a positive scale is
     * divided by its power of ten with enough extra quotient bits and a sticky bit for the remainder first.
     */
    private String bigNumberDoubles(boolean valueOfDouble) {
        return runtimeSource.load("avr/big-number-doubles.cpp") + (valueOfDouble ? """

                extern "C" int32_t juno_big_decimal_value_of_double(juno_f64 value) {
                  if (juno_f64_exp(value) == 0x7FF) return juno_big_number_format("Infinite or NaN");
                  char text[40];
                  juno_format_double(text, value);
                  return juno_bd_parse(text);
                }
                """ : "");
    }

    /** Wi-Fi, UDP, the HTTP/HTTPS client and the TLS email clients over WiFiNINA. */
    private void appendNetworkHelpers(StringBuilder shim) {
        if (uses(ShimFeature.WIFI)) {
            shim.append(core.wifiHelpers());
        }
        if (uses(ShimFeature.UDP)) {
            shim.append(networkShimLibraries.udpHelpers(core.udpDeclaration()));
        }
        if (uses(ShimFeature.STRING_FROM_BYTES)) {
            shim.append(shimLibraries.stringFromBytesHelpers());
        }
        if (usesAny(ShimFeature.HTTPS, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.httpsHelpers());
        }
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS)) {
            shim.append(networkShimLibraries.httpHelpers(features, core.httpsClientType(),
                    ioRuntime.usesWorker(core, features)));
        }
        // Smtp.sendTls and Pop3Client, exactly as on the UNO R4 WiFi: the board's TLS client is WiFiSSLClient.
        if (usesAny(ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(networkShimLibraries.emailHelpers(features, core.httpsClientDeclaration()));
        }
    }

    /** {@code juno_format_double} and {@code String.valueOf(double)}: the Thumb-2 algorithm, step for step, on binary64. */
    private String doubleFormatting() {
        return runtimeSource.load("avr/double-formatting.cpp").replace("${BILLION}", f64(1.0e9)).replace("${MILLION}", f64(1000000.0));
    }

    private String floatingAppends() {
        return runtimeSource.load("avr/floating-appends.cpp");
    }

    /** Java {@code float} arithmetic in binary64, rounded once to binary32 (exact; see {@link SoftFloatRuntime}). */
    private String floatHelpers() {
        return runtimeSource.load("avr/float.cpp");
    }

    private String doubleHelpers() {
        return runtimeSource.load("avr/double.cpp");
    }

    private final Map<Intrinsic, String> floatingMath = new EnumMap<>(Intrinsic.class);

    private void defineFloatingMath() {
        mathFunction(Intrinsic.MATH_ABS_FLOAT, "juno_f32", "juno_f32 a", "return a & 0x7FFFFFFFul;");
        mathFunction(Intrinsic.MATH_ABS_DOUBLE, "juno_f64", "juno_f64 a", "return a & 0x7FFFFFFFFFFFFFFFull;");
        mathFunction(Intrinsic.MATH_MIN_FLOAT, "juno_f32", "juno_f32 a, juno_f32 b",
                "return juno_java_min(juno_f32_wide(a), juno_f32_wide(b)) == juno_f32_wide(a) ? a : b;");
        mathFunction(Intrinsic.MATH_MIN_DOUBLE, "juno_f64", "juno_f64 a, juno_f64 b", "return juno_java_min(a, b);");
        mathFunction(Intrinsic.MATH_MAX_FLOAT, "juno_f32", "juno_f32 a, juno_f32 b",
                "return juno_java_max(juno_f32_wide(a), juno_f32_wide(b)) == juno_f32_wide(a) ? a : b;");
        mathFunction(Intrinsic.MATH_MAX_DOUBLE, "juno_f64", "juno_f64 a, juno_f64 b", "return juno_java_max(a, b);");
        mathFunction(Intrinsic.MATH_CLAMP_FLOAT, "juno_f32", "juno_f32 value, juno_f32 lo, juno_f32 hi",
                "return juno_f64_to_f32(juno_java_clamp(juno_f32_wide(value), juno_f32_wide(lo), juno_f32_wide(hi)));");
        mathFunction(Intrinsic.MATH_CLAMP_DOUBLE, "juno_f64", "juno_f64 value, juno_f64 lo, juno_f64 hi",
                "return juno_java_clamp(value, lo, hi);");
        mathFunction(Intrinsic.MATH_SIGNUM_FLOAT, "juno_f32", "juno_f32 a",
                "if (juno_f64_is_nan(juno_f32_wide(a)) || (a << 1) == 0) return a; "
                        + "return (a >> 31) != 0 ? 0xBF800000ul : 0x3F800000ul;");
        mathFunction(Intrinsic.MATH_SIGNUM_DOUBLE, "juno_f64", "juno_f64 a",
                "if (juno_f64_is_nan(a) || (a << 1) == 0) return a; "
                        + "return (a >> 63) != 0 ? ${MINUS_ONE} : ${ONE};");
        // x - floor(x) is exact (Sterbenz), so ties land on exactly 0.5 and round up, as Java requires; the
        // float overload adds 1 in binary32.
        mathFunction(Intrinsic.MATH_ROUND_FLOAT, "int32_t", "juno_f32 a",
                "juno_f64 x = juno_f32_wide(a); if (juno_f64_is_nan(x)) return 0; "
                        + "juno_f64 f = juno_f64_round_integral(x, false); "
                        + "juno_f64 r = juno_f64_compare(juno_f64_sub(x, f), ${HALF}) >= 0 "
                        + "? juno_f32_wide(juno_f64_to_f32(juno_f64_add(f, ${ONE}))) : f; "
                        + "return juno_f64_to_i32(r);");
        mathFunction(Intrinsic.MATH_ROUND_DOUBLE, "int64_t", "juno_f64 a",
                "if (juno_f64_is_nan(a)) return 0; juno_f64 f = juno_f64_round_integral(a, false); "
                        + "juno_f64 r = juno_f64_compare(juno_f64_sub(a, f), ${HALF}) >= 0 ? juno_f64_add(f, ${ONE}) : f; "
                        + "return juno_f64_to_i64(r);");
        mathFunction(Intrinsic.MATH_FLOOR, "juno_f64", "juno_f64 a", "return juno_f64_round_integral(a, false);");
        mathFunction(Intrinsic.MATH_CEIL, "juno_f64", "juno_f64 a", "return juno_f64_round_integral(a, true);");
        mathFunction(Intrinsic.MATH_SQRT, "juno_f64", "juno_f64 a", "return juno_f64_sqrt(a);");
        mathFunction(Intrinsic.MATH_TO_RADIANS, "juno_f64", "juno_f64 a", "return juno_f64_mul(a, ${RADIANS});");
        mathFunction(Intrinsic.MATH_TO_DEGREES, "juno_f64", "juno_f64 a", "return juno_f64_mul(a, ${DEGREES});");
    }

    private void mathFunction(Intrinsic intrinsic, String returnType, String parameters, String body) {
        floatingMath.put(intrinsic, "extern \"C\" " + returnType + " " + mathRuntime.symbol(intrinsic) + "("
                + parameters + ") { " + body + " }\n");
    }

    /**
     * {@code java.lang.Math}: the integer overloads exactly as on Thumb-2, the floating-point ones on binary64. The
     * transcendental functions need {@code Capability.DOUBLE_MATH}, which this board lacks.
     */
    private String mathHelpers() {
        StringBuilder math = new StringBuilder(runtimeSource.load("avr/math.cpp")).append(mathRuntime.longPrelude());
        for (Intrinsic intrinsic : Intrinsic.values()) {
            if (!usedMath.contains(intrinsic)) {
                continue;
            }
            String source = mathRuntime.integerSource(intrinsic);
            if (source == null) {
                source = floatingMath.get(intrinsic);
            }
            if (source == null) {
                throw Architecture.AVR.unsupported(intrinsic + " (no double-precision libm)");
            }
            math.append(source.replace("JUNO_ASM_ABI ", "")
                    .replace("${ONE}", f64(1.0))
                    .replace("${MINUS_ONE}", f64(-1.0))
                    .replace("${HALF}", f64(0.5))
                    .replace("${RADIANS}", f64(0.017453292519943295))
                    .replace("${DEGREES}", f64(57.29577951308232)));
        }
        return math.toString();
    }
}
