package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.intrinsic.Intrinsic;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates the {@code extern "C"} C++ runtime shim compiled alongside the generated assembly:
 * wrappers around things the assembly can't call directly — the arena allocator/panic handler and
 * {@code ArduinoLEDMatrix} (a C++-only object with inline-only methods and private timer/frame
 * state — no stable symbol to call without knowing its private layout) — plus every optional
 * helper block ({@link ShimLibraries}, {@link NetworkShimLibraries}) and library include the
 * lowered program's {@link ShimFeature}s call for. Optional parts are decided here, at
 * Java-string-generation time, so an unused feature costs no flash, RAM or build dependency.
 */
public final class RuntimeShim {
    private final ScopedValueRuntime scopedValueRuntime = new ScopedValueRuntime();
    private final AtomicRuntime atomicRuntime = new AtomicRuntime();
    private final MathRuntime mathRuntime = new MathRuntime();
    private final BigNumberRuntime bigNumberRuntime = new BigNumberRuntime();
    private final UuidRuntime uuidRuntime = new UuidRuntime();
    private final LocalDateTimeRuntime localDateTimeRuntime = new LocalDateTimeRuntime();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();
    private final IoRuntime ioRuntime = new IoRuntime();
    private final NetworkShimLibraries networkShimLibraries = new NetworkShimLibraries();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final AllocatorRuntime allocatorRuntime = new AllocatorRuntime();

    private final CoreRuntime core;
    private final boolean gcLoggingEnabled;
    private final Set<ShimFeature> features;
    private final Set<Intrinsic> usedMath;
    private final List<String> throwableClasses;
    private final Optional<WatchdogRuntime> watchdog;
    private final RuntimeConfig config;

    /**
     * @param core {@code board}'s per-core runtime glue (see {@code CoreRuntimes.of})
     * @param config the arena and task-stack capacities
     * @param features every optional shim feature the lowered program uses
     * @param usedMath every {@code java.lang.Math} intrinsic the lowered program calls
     * @param watchdogTimeoutMillis the entry point's {@code @Watchdog} timeout, if it has one
     */
    public RuntimeShim(CoreRuntime core, RuntimeConfig config, boolean gcLoggingEnabled,
                Set<ShimFeature> features,
                Set<Intrinsic> usedMath, List<String> throwableClasses, Optional<Integer> watchdogTimeoutMillis) {
        this.core = core;
        this.config = config;
        this.gcLoggingEnabled = gcLoggingEnabled;
        // The UUID helpers raise a Java exception for an invalid value, so a UUID program needs the exception helpers too.
        this.features = features.contains(ShimFeature.UUID) ? withExceptions(features) : features;
        this.usedMath = usedMath;
        this.throwableClasses = throwableClasses;
        this.watchdog = watchdogTimeoutMillis.map(core::watchdog);
    }

    public String generate() {
        StringBuilder shim = new StringBuilder();
        shim.append(runtimeSource.load("thumb2/shim-header.cpp").replace("${JUNO_LED_MATRIX_INCLUDE}",
                uses(ShimFeature.LED_MATRIX) ? "#include \"Arduino_LED_Matrix.h\"\n" : ""));
        appendIncludes(shim);
        shim.append(coreRuntime());
        appendHelpers(shim);
        return shim.toString();
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
        // Mouse is an optional library (arduino-cli lib install Mouse), unlike the core-bundled
        // LED matrix/Serial above — only pull it in when the program actually uses it, so every
        // other generated sketch keeps compiling without that library installed.
        if (uses(ShimFeature.MOUSE)) {
            shim.append("#include <Mouse.h>\n");
        }
        if (uses(ShimFeature.M5_DEVICE)) {
            shim.append("#include <M5Cardputer.h>\n");
        }
        if (uses(ShimFeature.SERVO)) {
            shim.append("#include <Servo.h>\n");
        }
        // ArduinoBLE is another optional library (arduino-cli lib install ArduinoBLE), like Mouse above.
        if (uses(ShimFeature.LEGO_POWERED_UP)) {
            shim.append(core.bleIncludes());
        }
        if (uses(ShimFeature.I2C)) {
            shim.append("#include <Wire.h>\n");
        }
        if (usesAny(ShimFeature.WIFI, ShimFeature.UDP, ShimFeature.TCP, ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.HTTP_SERVER,
                ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.wifiIncludes(usesAny(ShimFeature.UDP, ShimFeature.SNTP)));
        }
        if (uses(ShimFeature.SD)) {
            shim.append("#include <SPI.h>\n#include <SdFat.h>\n");
        }
        if (uses(ShimFeature.THREADS)) {
            shim.append(core.threadIncludes());
        }
        if (usesAny(ShimFeature.HTTPS, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.httpsInclude());
        }
        // ESP_SSLClient is a third-party library (arduino-cli lib install ESP_SSLClient), the same
        // manual-install pattern as Mouse above: the UNO R4 WiFi's native WiFiSSLClient can only
        // negotiate TLS from the first byte, so Smtp's STARTTLS upgrade (mail submission on port
        // 587) needs this wrapper around a plain WiFiClient instead.
        if (uses(ShimFeature.SMTP)) {
            shim.append("#include <ESP_SSLClient.h>\n");
        }
        if (uses(ShimFeature.HTTP_SERVER)) {
            shim.append("#include <new>\n");
        }
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.HTTP_SERVER,
                ShimFeature.RUNTIME_STRINGS, ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3,
                ShimFeature.SD)) {
            shim.append("#include <string.h>\n");
        }
        if (usesAny(ShimFeature.FLOAT, ShimFeature.DOUBLE, ShimFeature.RUNTIME_STRINGS,
                ShimFeature.STRING_BUILDER) || !usedMath.isEmpty()) {
            shim.append("#include <math.h>\n");
        }
        if (uses(ShimFeature.BIG_NUMBERS)) {
            shim.append("#include <stdlib.h>\n");
        }
        watchdog.ifPresent(watchdogRuntime -> shim.append(watchdogRuntime.includes()));
    }

    /** Panic handler, yield hook, watchdog, arena allocator and collector, LED matrix and Serial. */
    private String coreRuntime() {
        String watchdogRefresh = watchdog.isPresent() ? "juno_watchdog_refresh();" : "";
        String watchdogPanicMessage = watchdog.map(watchdogRuntime ->
                "Serial.println(\"[juno-watchdog] panic: board will reset via watchdog in "
                        + watchdogRuntime.effectiveTimeoutMillis() + "ms\");").orElse("");
        String watchdogFunctions = watchdog.map(watchdogRuntime -> """

                extern "C" void juno_watchdog_refresh() {
                  ${JUNO_WATCHDOG_REFRESH}
                }

                extern "C" void juno_watchdog_begin() {
                  ${JUNO_WATCHDOG_BEGIN}
                }
                """.replace("${JUNO_WATCHDOG_REFRESH}", watchdogRuntime.refresh())
                .replace("${JUNO_WATCHDOG_BEGIN}", watchdogRuntime.begin())).orElse("");
        // With threads every live thread's stack is a root, not just the one running (see ThreadRuntime).
        String gcStackScan = uses(ShimFeature.THREADS)
                ? "juno_thread_gc_scan(&stackMarker);"
                : "juno_gc_scan_range(&stackMarker, reinterpret_cast<const uint8_t*>(juno_gc_stack_top));";
        String gcThreadDeclaration = uses(ShimFeature.THREADS)
                ? "static void juno_thread_gc_scan(const uint8_t* marker);\n"
                : "";
        String ledMatrixFunctions = ledMatrixFunctions();
        String yieldFunction = watchdog.isPresent() ? core.watchdogYieldFunction() : core.yieldFunction();
        String watchdogRefreshDeclaration = watchdog.isPresent()
                ? "extern \"C\" void juno_watchdog_refresh();\n" : "";
        return runtimeSource.load("thumb2/panic.cpp")
                .replace("${JUNO_YIELD_FUNCTION}", watchdogRefreshDeclaration + yieldFunction)
                .replace("${JUNO_WATCHDOG_REFRESH}", watchdogRefresh)
                .replace("${JUNO_WATCHDOG_PANIC_MESSAGE}", watchdogPanicMessage)
                .replace("${JUNO_WATCHDOG_BEGIN_FUNCTION}", watchdogFunctions)
                + allocatorRuntime.source(config.arenaBytes(), 4, gcLoggingEnabled, gcStackScan, gcThreadDeclaration)
                + runtimeSource.load("thumb2/serial.cpp").replace("${JUNO_LED_MATRIX_FUNCTIONS}", ledMatrixFunctions);
    }

    private String ledMatrixFunctions() {
        // The LED matrix helpers need the board's Arduino_LED_Matrix library; the linker already
        // rejects LedMatrix calls on boards without one, and programs that never call it skip them.
        if (!uses(ShimFeature.LED_MATRIX)) {
            return "";
        }
        int words = core.ledMatrixWords();
        String parameters = "int32_t word0, int32_t word1, int32_t word2, int32_t word3";
        String values = IntStream.range(0, words)
                .mapToObj(index -> "    static_cast<uint32_t>(word" + index + ")")
                .collect(Collectors.joining(",\n"));
        // A frame has fewer words than the intrinsic passes on boards whose matrix fits in three.
        String unused = IntStream.range(words, 4)
                .mapToObj(index -> "  static_cast<void>(word" + index + ");\n")
                .collect(Collectors.joining());
        return runtimeSource.load("common/led-matrix.cpp").replace("${JUNO_LED_MATRIX_TYPE}", core.ledMatrixType())
                .replace("${JUNO_LED_MATRIX_PARAMETERS}", parameters)
                .replace("${JUNO_LED_MATRIX_UNUSED}", unused)
                .replace("${JUNO_LED_MATRIX_VALUES}", values)
                .replace("${JUNO_LED_MATRIX_WORDS}", Integer.toString(words));
    }

    private void appendPeripheralHelpers(StringBuilder shim) {
        shim.append(ioRuntime.preludeFor(core, features));
        if (uses(ShimFeature.M5_DEVICE)) {
            shim.append(runtimeSource.load("common/m5-device.cpp"));
        }
        if (uses(ShimFeature.M5_DISPLAY)) {
            shim.append(runtimeSource.load("common/m5-display.cpp"));
        }
        if (uses(ShimFeature.M5_KEYBOARD)) {
            shim.append(runtimeSource.load("common/m5-keyboard.cpp"));
        }
        if (uses(ShimFeature.M5_AUDIO)) {
            shim.append(runtimeSource.load("common/m5-audio.cpp"));
        }
        if (uses(ShimFeature.M5_BATTERY)) {
            shim.append(runtimeSource.load("common/m5-battery.cpp"));
        }
        if (uses(ShimFeature.MOUSE)) {
            shim.append(shimLibraries.mouseHelpers());
        }
        if (uses(ShimFeature.SERVO)) {
            shim.append(shimLibraries.servoHelpers());
        }
        if (uses(ShimFeature.LEGO_POWERED_UP)) {
            shim.append(core.bleHelpers());
            shim.append(shimLibraries.legoPoweredUpHelpers(uses(ShimFeature.THREADS), core.bleNames()));
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
    }

    private void appendHelpers(StringBuilder shim) {
        if (usesAny(ShimFeature.RUNTIME_STRINGS, ShimFeature.STRING_BUILDER)) {
            shim.append(shimLibraries.runtimeStringHelpers());
        }
        if (uses(ShimFeature.STRING_BUILDER)) {
            shim.append(shimLibraries.stringBuilderHelpers());
        }
        appendPeripheralHelpers(shim);
        ShimFeatureBlock.appendUsed(shim, features, List.of(
                new ShimFeatureBlock(ShimFeature.MEMORY, shimLibraries::memoryHelpers),
                new ShimFeatureBlock(ShimFeature.FLASH, core::flashHelpers),
                new ShimFeatureBlock(ShimFeature.RANDOM, shimLibraries::randomHelpers),
                new ShimFeatureBlock(ShimFeature.WIFI, core::wifiHelpers),
                new ShimFeatureBlock(ShimFeature.UDP, () -> networkShimLibraries.udpHelpers(core.udpDeclaration())),
                new ShimFeatureBlock(ShimFeature.TCP, () -> networkShimLibraries.tcpHelpers(core.tcpClientHelpers())),
                new ShimFeatureBlock(ShimFeature.SD, () -> core.sdHelpers(4, 16, 32, 64, 96)),
                new ShimFeatureBlock(ShimFeature.SD_CAPACITY, shimLibraries::sdCapacityHelpers),
                new ShimFeatureBlock(ShimFeature.LONG, shimLibraries::longHelpers),
                new ShimFeatureBlock(ShimFeature.FLOAT, shimLibraries::floatHelpers),
                new ShimFeatureBlock(ShimFeature.DOUBLE, shimLibraries::doubleHelpers)));
        if (!usedMath.isEmpty()) {
            shim.append(mathRuntime.helpers(usedMath));
        }
        ShimFeatureBlock.appendUsed(shim, features, List.of(
                new ShimFeatureBlock(ShimFeature.EXCEPTIONS, () -> shimLibraries.exceptionHelpers(throwableClasses)),
                new ShimFeatureBlock(ShimFeature.RANDOM_ACCESS_FILE, () -> shimLibraries.randomAccessFileHelpers(
                        throwableClasses.indexOf("java/io/IOException"),
                        throwableClasses.indexOf("java/io/FileNotFoundException"),
                        throwableClasses.indexOf("java/io/EOFException"),
                        throwableClasses.indexOf("java/lang/IndexOutOfBoundsException"))),
                new ShimFeatureBlock(ShimFeature.STRING_FROM_BYTES, shimLibraries::stringFromBytesHelpers)));
        if (usesAny(ShimFeature.HTTPS, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.httpsHelpers());
        }
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS)) {
            shim.append(networkShimLibraries.httpHelpers(features, core.httpClientType(), core.httpsClientType(),
                    ioRuntime.usesWorker(core, features)));
        } else if (uses(ShimFeature.HTTP_TIMEOUT)) {
            shim.append(networkShimLibraries.httpTimeoutStub());
        }
        if (uses(ShimFeature.HTTP_SERVER)) {
            shim.append(networkShimLibraries.httpServerHelpers(core.httpServerTransport(),
                    ioRuntime.usesWorker(core, features)));
        }
        if (usesAny(ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(networkShimLibraries.emailHelpers(features, core.httpsClientDeclaration()));
        }
        ShimFeatureBlock.appendUsed(shim, features, List.of(
                new ShimFeatureBlock(ShimFeature.ATOMICS, atomicRuntime::helpers),
                new ShimFeatureBlock(ShimFeature.UUID_RANDOM, core::uuidRandomHelpers),
                new ShimFeatureBlock(ShimFeature.UUID, () -> uuidRuntime.helpers(
                        throwableClasses.indexOf("java/lang/IllegalArgumentException"),
                        uses(ShimFeature.UUID_RANDOM)))));
        appendLocalDateTimeHelpers(shim);
        appendConcurrencyHelpers(shim);
    }

    private void appendLocalDateTimeHelpers(StringBuilder shim) {
        if (uses(ShimFeature.LOCAL_DATE_TIME)) {
            shim.append(localDateTimeRuntime.helpers(uses(ShimFeature.LOCAL_DATE_TIME_NOW),
                    uses(ShimFeature.LOCAL_DATE_TIME_CONFIG),
                    uses(ShimFeature.SNTP) ? core.sntpUdpDeclaration() : ""));
        }
    }

    /** Scoped-value bindings, then the thread scheduler that swaps them (it refers to the binding stack). */
    private void appendConcurrencyHelpers(StringBuilder shim) {
        if (uses(ShimFeature.BIG_NUMBERS)) {
            shim.append(bigNumberRuntime.handleHelpers());
            shim.append(bigNumberRuntime.helpers(
                    throwableClasses.indexOf("java/lang/ArithmeticException"),
                    throwableClasses.indexOf("java/lang/NumberFormatException"),
                    throwableClasses.indexOf("java/lang/IllegalArgumentException"),
                    uses(ShimFeature.BIG_DECIMAL_DOUBLE)));
        }
        if (uses(ShimFeature.SCOPED_VALUES)) {
            shim.append(scopedValueRuntime.bindings(uses(ShimFeature.THREAD_ENTRY),
                    uses(ShimFeature.SCOPED_CALL_ENTRY),
                    throwableClasses.indexOf("java/util/NoSuchElementException")));
        }
        if (uses(ShimFeature.THREADS)) {
            shim.append(core.threadPort(config));
            shim.append(ioRuntime.workerPortFor(core, features));
            shim.append(threadRuntime.scheduler(core.preemptsAsynchronously(), ioRuntime.usesWorker(core, features),
                    core.maxConcurrentHttpRequests(), core.delayMillisFunction(), "juno_gc_scan_range",
                    uses(ShimFeature.EXCEPTIONS),
                    uses(ShimFeature.THREAD_ENTRY), uses(ShimFeature.TASK_CALLABLE_ENTRY),
                    uses(ShimFeature.STRUCTURED_TASKS), uses(ShimFeature.SCOPED_VALUES),
                    throwableClasses.indexOf("java/util/concurrent/ExecutionException"),
                    throwableClasses.indexOf("java/lang/IllegalStateException")));
        } else if (uses(ShimFeature.THREAD_BASICS)) {
            shim.append(threadRuntime.basics(core.delayMillisFunction()));
        }
    }

    private static Set<ShimFeature> withExceptions(Set<ShimFeature> features) {
        Set<ShimFeature> withExceptions = EnumSet.noneOf(ShimFeature.class);
        withExceptions.addAll(features);
        withExceptions.add(ShimFeature.EXCEPTIONS);
        return withExceptions;
    }
}
