package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.core.CoreRuntime;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.intrinsic.Intrinsic;

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
    private final Board board;
    private final CoreRuntime core;
    private final boolean gcLoggingEnabled;
    private final Set<ShimFeature> features;
    private final Set<Intrinsic> usedMath;
    private final List<String> throwableClasses;
    private final boolean usesWatchdog;
    private final int watchdogTimeoutMillis;
    private final RuntimeConfig config;

    /**
     * @param core {@code board}'s per-core runtime glue (see {@link CoreRuntime#of})
     * @param config the arena and task-stack capacities
     * @param features every optional shim feature the lowered program uses
     * @param usedMath every {@code java.lang.Math} intrinsic the lowered program calls
     * @param watchdogTimeoutMillis the entry point's {@code @Watchdog} timeout, if it has one
     */
    public RuntimeShim(Board board, CoreRuntime core, RuntimeConfig config, boolean gcLoggingEnabled,
                Set<ShimFeature> features,
                Set<Intrinsic> usedMath, List<String> throwableClasses, Optional<Integer> watchdogTimeoutMillis) {
        this.board = board;
        this.core = core;
        this.config = config;
        this.gcLoggingEnabled = gcLoggingEnabled;
        this.features = features;
        this.usedMath = usedMath;
        this.throwableClasses = throwableClasses;
        this.usesWatchdog = watchdogTimeoutMillis.isPresent();
        this.watchdogTimeoutMillis = watchdogTimeoutMillis.orElse(0);
    }

    public String generate() {
        StringBuilder shim = new StringBuilder();
        shim.append(RuntimeSource.load("thumb2/shim-header.cpp").replace("${JUNO_LED_MATRIX_INCLUDE}",
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
        if (uses(ShimFeature.SERVO)) {
            shim.append("#include <Servo.h>\n");
        }
        // ArduinoBLE is another optional library (arduino-cli lib install ArduinoBLE), like Mouse above.
        if (uses(ShimFeature.LEGO_POWERED_UP)) {
            shim.append("#include <ArduinoBLE.h>\n");
        }
        if (uses(ShimFeature.I2C)) {
            shim.append("#include <Wire.h>\n");
        }
        if (usesAny(ShimFeature.WIFI, ShimFeature.UDP, ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.HTTP_SERVER,
                ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.wifiIncludes(uses(ShimFeature.UDP)));
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
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.HTTP_SERVER, ShimFeature.JSON,
                ShimFeature.RUNTIME_STRINGS, ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3,
                ShimFeature.SD)) {
            shim.append("#include <string.h>\n");
        }
        if (usesAny(ShimFeature.FLOAT, ShimFeature.DOUBLE, ShimFeature.JSON, ShimFeature.RUNTIME_STRINGS,
                ShimFeature.STRING_BUILDER) || !usedMath.isEmpty()) {
            shim.append("#include <math.h>\n");
        }
        if (uses(ShimFeature.BIG_NUMBERS)) {
            shim.append("#include <stdlib.h>\n");
        }
        // WDT (like Arduino_LED_Matrix above) is bundled with the renesas_uno core, not a separate
        // library install — only pulled in for programs whose entry-point class carries @Watchdog.
        if (usesWatchdog) {
            shim.append("#include <WDT.h>\n");
        }
    }

    /** Panic handler, yield hook, watchdog, arena allocator and collector, LED matrix and Serial. */
    private String coreRuntime() {
        String watchdogRefresh = usesWatchdog ? "WDT.refresh();" : "";
        String watchdogPanicMessage = usesWatchdog
                ? "Serial.println(\"[juno-watchdog] panic: board will reset via watchdog in "
                        + watchdogTimeoutMillis + "ms\");"
                : "";
        String watchdogBeginFunction = usesWatchdog
                ? "\nextern \"C\" void juno_watchdog_begin() {\n  WDT.begin(" + watchdogTimeoutMillis + "u);\n}\n"
                : "";
        // With threads every live thread's stack is a root, not just the one running (see ThreadRuntime).
        String gcStackScan = uses(ShimFeature.THREADS)
                ? "juno_thread_gc_scan(&stackMarker);"
                : "juno_gc_scan_range(&stackMarker, reinterpret_cast<const uint8_t*>(juno_gc_stack_top));";
        String gcThreadDeclaration = uses(ShimFeature.THREADS)
                ? "static void juno_thread_gc_scan(const uint8_t* marker);\n"
                : "";
        String ledMatrixFunctions = ledMatrixFunctions();
        String yieldFunction = core.yieldFunction();
        return RuntimeSource.load("thumb2/panic.cpp").replace("${JUNO_YIELD_FUNCTION}", yieldFunction)
                .replace("${JUNO_WATCHDOG_REFRESH}", watchdogRefresh)
                .replace("${JUNO_WATCHDOG_PANIC_MESSAGE}", watchdogPanicMessage)
                .replace("${JUNO_WATCHDOG_BEGIN_FUNCTION}", watchdogBeginFunction)
                + allocator(config.arenaBytes(), gcLoggingEnabled, gcStackScan, gcThreadDeclaration)
                + RuntimeSource.load("thumb2/serial.cpp").replace("${JUNO_LED_MATRIX_FUNCTIONS}", ledMatrixFunctions);
    }

    /**
     * The arena and its conservative mark/sweep collector, {@code juno_alloc}, shared by every architecture: it
     * scans {@code uintptr_t}-wide words, so it finds 16-bit AVR references as well as 32-bit ARM ones.
     *
     * @param gcStackScan the statement scanning the live stack(s) from {@code stackMarker}
     * @param gcThreadDeclaration declarations that statement needs, or {@code ""}
     */
    static String allocator(int arenaBytes, boolean gcLoggingEnabled, String gcStackScan, String gcThreadDeclaration) {
        // Emitted (or not) once here, at Java-string-generation time — like every other optional
        // shim feature (ShimFeature.MOUSE, ShimFeature.WIFI, ...) — rather than a C++-level runtime branch, so a
        // program compiled without --gc-log/juno.gcLog pays zero extra flash/RAM/Serial time for it.
        String gcLogBefore = gcLoggingEnabled ? "uint32_t junoGcLogUsedBefore = juno_arena_used;" : "";
        String gcLogAfter = gcLoggingEnabled
                ? "Serial.print(\"[juno-gc] collect: used \"); Serial.print(junoGcLogUsedBefore); "
                        + "Serial.print(\" -> \"); Serial.println(juno_arena_used);"
                : "";
        return RuntimeSource.load("common/allocator.cpp").replace("${JUNO_ARENA_CAPACITY}", Integer.toString(arenaBytes))
                .replace("${JUNO_GC_STACK_SCAN}", gcStackScan)
                .replace("${JUNO_GC_THREAD_DECLARATION}", gcThreadDeclaration)
                .replace("${JUNO_GC_LOG_BEFORE}", gcLogBefore)
                .replace("${JUNO_GC_LOG_AFTER}", gcLogAfter);
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
        return RuntimeSource.load("common/led-matrix.cpp").replace("${JUNO_LED_MATRIX_TYPE}", core.ledMatrixType())
                .replace("${JUNO_LED_MATRIX_PARAMETERS}", parameters)
                .replace("${JUNO_LED_MATRIX_UNUSED}", unused)
                .replace("${JUNO_LED_MATRIX_VALUES}", values)
                .replace("${JUNO_LED_MATRIX_WORDS}", Integer.toString(words));
    }

    private void appendPeripheralHelpers(StringBuilder shim) {
        shim.append(IoRuntime.preludeFor(core, features));
        if (uses(ShimFeature.MOUSE)) {
            shim.append(ShimLibraries.mouseHelpers());
        }
        if (uses(ShimFeature.SERVO)) {
            shim.append(ShimLibraries.servoHelpers());
        }
        if (uses(ShimFeature.LEGO_POWERED_UP)) {
            shim.append(ShimLibraries.legoPoweredUpHelpers(uses(ShimFeature.THREADS)));
        }
        if (uses(ShimFeature.PARALLEL_BUS)) {
            shim.append(core.parallelBusHelpers());
        }
        if (uses(ShimFeature.INFRARED)) {
            shim.append(ShimLibraries.infraredHelpers());
        }
        if (uses(ShimFeature.I2C)) {
            shim.append(ShimLibraries.i2cHelpers());
        }
    }

    private void appendHelpers(StringBuilder shim) {
        if (usesAny(ShimFeature.RUNTIME_STRINGS, ShimFeature.STRING_BUILDER)) {
            shim.append(ShimLibraries.runtimeStringHelpers());
        }
        if (uses(ShimFeature.STRING_BUILDER)) {
            shim.append(ShimLibraries.stringBuilderHelpers());
        }
        appendPeripheralHelpers(shim);
        if (uses(ShimFeature.MEMORY)) {
            shim.append(ShimLibraries.memoryHelpers());
        }
        if (uses(ShimFeature.RANDOM)) {
            shim.append(ShimLibraries.randomHelpers());
        }
        if (uses(ShimFeature.WIFI)) {
            shim.append(core.wifiHelpers());
        }
        if (uses(ShimFeature.UDP)) {
            shim.append(NetworkShimLibraries.udpHelpers(core.udpDeclaration()));
        }
        if (uses(ShimFeature.SD)) {
            shim.append(ShimLibraries.sdHelpers());
        }
        if (uses(ShimFeature.LONG)) {
            shim.append(ShimLibraries.longHelpers());
        }
        if (uses(ShimFeature.FLOAT)) {
            shim.append(ShimLibraries.floatHelpers());
        }
        if (uses(ShimFeature.DOUBLE)) {
            shim.append(ShimLibraries.doubleHelpers());
        }
        if (!usedMath.isEmpty()) {
            shim.append(MathRuntime.helpers(usedMath));
        }
        if (uses(ShimFeature.EXCEPTIONS)) {
            shim.append(ShimLibraries.exceptionHelpers(throwableClasses));
        }
        if (uses(ShimFeature.RANDOM_ACCESS_FILE)) {
            shim.append(ShimLibraries.randomAccessFileHelpers(
                    throwableClasses.indexOf("java/io/IOException"),
                    throwableClasses.indexOf("java/io/FileNotFoundException"),
                    throwableClasses.indexOf("java/io/EOFException"),
                    throwableClasses.indexOf("java/lang/IndexOutOfBoundsException")));
        }
        if (uses(ShimFeature.JSON)) {
            shim.append(NetworkShimLibraries.jsonHelpers());
        }
        if (uses(ShimFeature.JSON_STRING_VALUE)) {
            shim.append(NetworkShimLibraries.jsonStringValueHelpers());
        }
        if (usesAny(ShimFeature.HTTPS, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.httpsHelpers());
        }
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS)) {
            shim.append(NetworkShimLibraries.httpHelpers(features, core.httpsClientType(),
                    IoRuntime.usesWorker(core, features)));
        }
        if (uses(ShimFeature.HTTP_SERVER)) {
            shim.append(NetworkShimLibraries.httpServerHelpers(core.httpServerTransport(),
                    IoRuntime.usesWorker(core, features)));
        }
        if (usesAny(ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(NetworkShimLibraries.emailHelpers(features, core.httpsClientDeclaration()));
        }
        if (uses(ShimFeature.ATOMICS)) {
            shim.append(AtomicRuntime.helpers());
        }
        appendConcurrencyHelpers(shim);
    }

    /** Scoped-value bindings, then the thread scheduler that swaps them (it refers to the binding stack). */
    private void appendConcurrencyHelpers(StringBuilder shim) {
        if (uses(ShimFeature.BIG_NUMBERS)) {
            shim.append(BigNumberRuntime.handleHelpers());
            shim.append(BigNumberRuntime.helpers(
                    throwableClasses.indexOf("java/lang/ArithmeticException"),
                    throwableClasses.indexOf("java/lang/NumberFormatException"),
                    throwableClasses.indexOf("java/lang/IllegalArgumentException"),
                    uses(ShimFeature.BIG_DECIMAL_DOUBLE)));
        }
        if (uses(ShimFeature.SCOPED_VALUES)) {
            shim.append(ScopedValueRuntime.bindings(uses(ShimFeature.THREAD_ENTRY),
                    uses(ShimFeature.SCOPED_CALL_ENTRY),
                    throwableClasses.indexOf("java/util/NoSuchElementException")));
        }
        if (uses(ShimFeature.THREADS)) {
            shim.append(core.threadPort(config));
            shim.append(IoRuntime.workerPortFor(core, features));
            shim.append(ThreadRuntime.scheduler(true, IoRuntime.usesWorker(core, features),
                    core.delayMillisFunction(), "juno_gc_scan_range",
                    uses(ShimFeature.EXCEPTIONS),
                    uses(ShimFeature.THREAD_ENTRY), uses(ShimFeature.TASK_CALLABLE_ENTRY),
                    uses(ShimFeature.STRUCTURED_TASKS), uses(ShimFeature.SCOPED_VALUES),
                    throwableClasses.indexOf("java/util/concurrent/ExecutionException"),
                    throwableClasses.indexOf("java/lang/IllegalStateException")));
        } else if (uses(ShimFeature.THREAD_BASICS)) {
            shim.append(ThreadRuntime.basics(core.delayMillisFunction()));
        }
    }
}
