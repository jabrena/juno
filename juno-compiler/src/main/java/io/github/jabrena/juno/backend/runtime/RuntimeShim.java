package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.ResolvedRuntimeConfig;
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
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class RuntimeShim {
    /**
     * The JEV1 decision-model evaluator, the same for every model: {@code juno_dm_use} checks a model's header and
     * length, and evaluation reads the bytes in place, wherever they are (a flash table or a PSRAM buffer). A JEV1 file
     * is big-endian: "JEV1", version 1, the action and feature counts, then per action a float scale, a float bias and
     * one signed byte per feature. The status values are {@code ModelStatus} ordinals.
     */
    private static final String DECISION_MODEL_ENGINE = """

            static constexpr int32_t JUNO_DM_OK = 0;
            static constexpr int32_t JUNO_DM_INVALID_HANDLE = 1;
            static constexpr int32_t JUNO_DM_INVALID_MODEL = 2;
            static constexpr int32_t JUNO_DM_HEADER_BYTES = 9;
            static const uint8_t* juno_dm_model = nullptr;
            static int32_t juno_dm_action_count = 0;
            static int32_t juno_dm_feature_count = 0;
            // Frees the model's own memory on unload: set only by a program that loads a model into PSRAM.
            static void (*juno_dm_release_model)() = nullptr;

            static float juno_dm_float_at(const uint8_t* bytes) {
              uint32_t bits = (static_cast<uint32_t>(bytes[0]) << 24) | (static_cast<uint32_t>(bytes[1]) << 16)
                  | (static_cast<uint32_t>(bytes[2]) << 8) | static_cast<uint32_t>(bytes[3]);
              float value;
              memcpy(&value, &bits, sizeof(value));
              return value;
            }

            extern "C" void juno_dm_unload() {
              if (juno_dm_release_model != nullptr) juno_dm_release_model();
              juno_dm_release_model = nullptr;
              juno_dm_model = nullptr;
              juno_dm_action_count = 0;
              juno_dm_feature_count = 0;
            }

            // Points the evaluator at size JEV1 bytes once their header and length check out.
            static int32_t juno_dm_use(const uint8_t* model, int32_t size) {
              juno_dm_unload();
              if (model == nullptr || size < JUNO_DM_HEADER_BYTES || memcmp(model, "JEV1", 4) != 0 || model[4] != 1) {
                return JUNO_DM_INVALID_MODEL;
              }
              int32_t actions = (static_cast<int32_t>(model[5]) << 8) | model[6];
              int32_t features = (static_cast<int32_t>(model[7]) << 8) | model[8];
              if (actions == 0 || features == 0
                  || static_cast<int64_t>(JUNO_DM_HEADER_BYTES) + static_cast<int64_t>(actions) * (8 + features)
                      != size) {
                return JUNO_DM_INVALID_MODEL;
              }
              juno_dm_model = model;
              juno_dm_action_count = actions;
              juno_dm_feature_count = features;
              return JUNO_DM_OK;
            }

            extern "C" int32_t juno_dm_actions() {
              return juno_dm_action_count;
            }

            extern "C" int32_t juno_dm_features() {
              return juno_dm_feature_count;
            }

            extern "C" int32_t juno_dm_evaluate(const int32_t* state, const int32_t* legalActions) {
              if (juno_dm_model == nullptr || state[-1] != juno_dm_feature_count) return -1;
              int32_t legalActionCount = legalActions[-1];
              int32_t rowSize = 8 + juno_dm_feature_count;
              int32_t selected = -1;
              float bestScore = 0.0f;
              for (int32_t index = 0; index < legalActionCount; index++) {
                int32_t action = legalActions[index];
                if (action < 0 || action >= juno_dm_action_count) continue;
                const uint8_t* row = juno_dm_model + JUNO_DM_HEADER_BYTES + action * rowSize;
                float scale = juno_dm_float_at(row);
                const int8_t* weights = reinterpret_cast<const int8_t*>(row + 8);
                float score = juno_dm_float_at(row + 4);
                for (int32_t feature = 0; feature < juno_dm_feature_count; feature++) {
                  if (state[feature] != 0) score += weights[feature] * scale * state[feature];
                }
                if (selected < 0 || score > bestScore) { selected = action; bestScore = score; }
              }
              return selected;
            }
            """;

    private final ScopedValueRuntime scopedValueRuntime = new ScopedValueRuntime();
    private final AtomicRuntime atomicRuntime = new AtomicRuntime();
    private final MathRuntime mathRuntime = new MathRuntime();
    private final BigNumberRuntime bigNumberRuntime = new BigNumberRuntime();
    private final UuidRuntime uuidRuntime = new UuidRuntime();
    private final DatagramRuntime datagramRuntime = new DatagramRuntime();
    private final SocketRuntime socketRuntime = new SocketRuntime();
    private final LocalDateTimeRuntime localDateTimeRuntime = new LocalDateTimeRuntime();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();
    private final IoRuntime ioRuntime = new IoRuntime();
    private final NetworkShimLibraries networkShimLibraries = new NetworkShimLibraries();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final AllocatorRuntime allocatorRuntime = new AllocatorRuntime();
    private final CarrierGcRuntime carrierGcRuntime = new CarrierGcRuntime();
    private final GpioPinRuntime gpioPinRuntime = new GpioPinRuntime();

    private final CoreRuntime core;
    private final boolean gcLoggingEnabled;
    private final Set<ShimFeature> features;
    private final Set<Intrinsic> usedMath;
    private final List<String> throwableClasses;
    private final Optional<WatchdogRuntime> watchdog;
    private final ResolvedRuntimeConfig config;
    private final int carrierCount;

    /**
     * @param core {@code board}'s per-core runtime glue (see {@code CoreRuntimes.of})
     * @param config the arena and task-stack capacities
     * @param features every optional shim feature the lowered program uses
     * @param usedMath every {@code java.lang.Math} intrinsic the lowered program calls
     * @param watchdogTimeoutMillis the entry point's {@code @Watchdog} timeout, if it has one
     */
    public RuntimeShim(CoreRuntime core, ResolvedRuntimeConfig config, boolean gcLoggingEnabled,
                Set<ShimFeature> features,
                Set<Intrinsic> usedMath, List<String> throwableClasses, Optional<Integer> watchdogTimeoutMillis) {
        this.core = core;
        this.config = config;
        this.carrierCount = config.carrierCount(features.contains(ShimFeature.THREADS));
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
        // other generated sketch keeps compiling without that library installed. A core with its
        // own HID stack (ESP32's USBHIDMouse) names its headers instead.
        if (uses(ShimFeature.MOUSE)) {
            shim.append(core.mouseIncludes());
        }
        if (uses(ShimFeature.M5_DEVICE)) {
            shim.append("#include <M5Cardputer.h>\n");
        }
        if (uses(ShimFeature.SERVO)) {
            shim.append("#include <Servo.h>\n");
        }
        if (uses(ShimFeature.CAMERA)) {
            shim.append(core.cameraIncludes());
        }
        if (usesAny(ShimFeature.PSRAM, ShimFeature.LANGUAGE_MODEL)) {
            shim.append("#include <esp_heap_caps.h>\n");
        }
        // ArduinoBLE is another optional library (arduino-cli lib install ArduinoBLE), like Mouse above.
        if (uses(ShimFeature.BLUETOOTH_LE)) {
            shim.append(core.bleIncludes());
        }
        if (uses(ShimFeature.I2C)) {
            shim.append("#include <Wire.h>\n");
        }
        if (uses(ShimFeature.SE050)) {
            shim.append("#include <Arduino_SE05X.h>\n");
        }
        if (uses(ShimFeature.NVS)) {
            shim.append("#include <Preferences.h>\n");
        }
        if (uses(ShimFeature.SPI)) {
            shim.append("#include <SPI.h>\n");
        }
        if (usesAny(ShimFeature.WIFI, ShimFeature.UDP, ShimFeature.TCP, ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.HTTP_SERVER,
                ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.wifiIncludes(usesAny(ShimFeature.UDP, ShimFeature.SNTP)));
        }
        if (uses(ShimFeature.SD)) {
            shim.append(core.sdIncludes());
        }
        if (uses(ShimFeature.THREADS)) {
            shim.append(core.threadIncludes());
        }
        if (usesAny(ShimFeature.HTTPS, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(core.httpsInclude());
        }
        // Smtp's STARTTLS upgrade (mail submission on port 587) needs a client that starts TLS on a plain connection.
        // The default is ESP_SSLClient, a third-party library (arduino-cli lib install ESP_SSLClient), the same
        // manual-install pattern as Mouse above, because the UNO R4 WiFi's native WiFiSSLClient can only negotiate TLS
        // from the first byte. A core with its own upgrade overrides smtpStarttlsInclude.
        if (uses(ShimFeature.SMTP)) {
            shim.append(core.smtpStarttlsInclude());
        }
        if (uses(ShimFeature.HTTP_SERVER)) {
            shim.append("#include <new>\n");
        }
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS, ShimFeature.HTTP_SERVER,
                ShimFeature.RUNTIME_STRINGS, ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3,
                ShimFeature.SD, ShimFeature.FLOAT, ShimFeature.DOUBLE, ShimFeature.SYSTEM_ARRAYCOPY,
                ShimFeature.DECISION_MODEL)) {
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
                .replace("${JUNO_PANIC_HALT}", core.panicHalt())
                .replace("${JUNO_WATCHDOG_BEGIN_FUNCTION}", watchdogFunctions)
                + allocatorRuntime.source(config.arenaBytes(), 4, gcLoggingEnabled, gcStackScan, gcThreadDeclaration,
                        carrierCount)
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
        shim.append(ioRuntime.preludeFor(features, carrierCount, nativeCalls()));
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
            shim.append(core.mouseHelpers());
            shim.append(shimLibraries.mouseHelpers());
        }
        if (uses(ShimFeature.SERVO)) {
            shim.append(shimLibraries.servoHelpers());
        }
        if (uses(ShimFeature.BLUETOOTH_LE)) {
            shim.append(core.bleHelpers());
            shim.append(shimLibraries.bleCentralHelpers(uses(ShimFeature.THREADS), core.bleNames()));
        }
        if (uses(ShimFeature.CAMERA)) {
            shim.append(core.cameraHelpers());
        }
        if (usesAny(ShimFeature.PSRAM, ShimFeature.LANGUAGE_MODEL)) {
            shim.append(core.psramHelpers());
        }
        if (uses(ShimFeature.LANGUAGE_MODEL)) {
            shim.append(core.languageModelHelpers());
        }
        if (uses(ShimFeature.LANGUAGE_MODEL_DEFAULT)) {
            shim.append(DefaultLanguageModel.bundled().source());
        }
        if (uses(ShimFeature.DECISION_MODEL)) {
            shim.append(DECISION_MODEL_ENGINE);
        }
        if (uses(ShimFeature.DECISION_MODEL_PSRAM)) {
            shim.append("""

                    // DecisionModel.begin(SdCard, String): the evaluator reads the JEV1 copy in place, in PSRAM. Once it is
                    // accepted the engine owns the buffer and frees it on unload; a refused buffer stays the caller's.
                    static int32_t juno_dm_psram_handle = 0;

                    static void juno_dm_release_psram() {
                      juno_psram_release(juno_dm_psram_handle);
                      juno_dm_psram_handle = 0;
                    }

                    extern "C" int32_t juno_dm_begin_psram(int32_t handle, int32_t size) {
                      juno_dm_unload();
                      JunoPsramSlot* slot = juno_psram_find(handle);
                      if (slot == nullptr) return JUNO_DM_INVALID_HANDLE;
                      if (size < 0 || static_cast<uint32_t>(size) > slot->capacity) return JUNO_DM_INVALID_MODEL;
                      int32_t status = juno_dm_use(static_cast<const uint8_t*>(slot->pointer), size);
                      if (status == JUNO_DM_OK) {
                        juno_dm_psram_handle = handle;
                        juno_dm_release_model = juno_dm_release_psram;
                      }
                      return status;
                    }
                    """);
        }
        if (uses(ShimFeature.SERIAL_WRITE)) {
            shim.append("""

                    extern "C" void juno_serial_write(int32_t value) {
                      Serial.write(static_cast<uint8_t>(value));
                    }
                    """);
        }
        if (uses(ShimFeature.PARALLEL_BUS)) {
            shim.append(core.parallelBusHelpers());
        }
        if (uses(ShimFeature.INFRARED)) {
            shim.append(shimLibraries.infraredHelpers());
        }
        if (uses(ShimFeature.I2C)) {
            shim.append(shimLibraries.i2cHelpers(core.i2cPinSetup(), core.i2cBus()));
        }
        if (uses(ShimFeature.SE050)) {
            shim.append(shimLibraries.se050Helpers());
        }
        if (uses(ShimFeature.NVS)) {
            shim.append(core.nvsHelpers());
        }
        if (uses(ShimFeature.PICO_TRUSTZONE)) {
            shim.append(core.trustZoneHelpers());
        }
        if (uses(ShimFeature.SPI)) {
            shim.append(shimLibraries.spiHelpers(core));
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
                new ShimFeatureBlock(ShimFeature.SYSTEM_ARRAYCOPY,
                        () -> runtimeSource.load("common/system-arraycopy.cpp")),
                new ShimFeatureBlock(ShimFeature.FLASH, core::flashHelpers),
                new ShimFeatureBlock(ShimFeature.RANDOM, shimLibraries::randomHelpers),
                new ShimFeatureBlock(ShimFeature.WIFI, core::wifiHelpers),
                new ShimFeatureBlock(ShimFeature.UDP, () -> networkShimLibraries.udpHelpers(core.udpDeclaration())),
                new ShimFeatureBlock(ShimFeature.TCP, () -> networkShimLibraries.tcpHelpers(core.tcpClientHelpers())),
                new ShimFeatureBlock(ShimFeature.SD, () -> core.sdHelpers(4, 16, 32, 64, 96)),
                new ShimFeatureBlock(ShimFeature.SD_CAPACITY, shimLibraries::sdCapacityHelpers),
                new ShimFeatureBlock(ShimFeature.LONG, shimLibraries::longHelpers),
                new ShimFeatureBlock(ShimFeature.FLOAT, shimLibraries::floatHelpers),
                new ShimFeatureBlock(ShimFeature.DOUBLE,
                        () -> core.doubleHelpers(carrierCount).orElseGet(shimLibraries::doubleHelpers))));
        if (!usedMath.isEmpty()) {
            shim.append(mathRuntime.helpers(usedMath));
        }
        ShimFeatureBlock.appendUsed(shim, features, List.of(
                new ShimFeatureBlock(ShimFeature.EXCEPTIONS, () -> shimLibraries.exceptionHelpers(throwableClasses,
                        carrierCount)),
                new ShimFeatureBlock(ShimFeature.GPIO_PIN_LOOKUP, () -> gpioPinRuntime.helpers(core,
                        throwableClasses.indexOf("java/lang/IllegalArgumentException"))),
                new ShimFeatureBlock(ShimFeature.GPIO_PULSE_IN, () -> shimLibraries.pulseInHelpers(core.pulseInPin())),
                new ShimFeatureBlock(ShimFeature.RANDOM_ACCESS_FILE, () -> shimLibraries.randomAccessFileHelpers(
                        throwableClasses.indexOf("java/io/IOException"),
                        throwableClasses.indexOf("java/io/FileNotFoundException"),
                        throwableClasses.indexOf("java/io/EOFException"),
                        throwableClasses.indexOf("java/lang/IndexOutOfBoundsException"))),
                new ShimFeatureBlock(ShimFeature.DATAGRAM, () -> datagramRuntime.helpers(
                        throwableClasses.indexOf("java/io/IOException"),
                        throwableClasses.indexOf("java/lang/IllegalArgumentException"))),
                new ShimFeatureBlock(ShimFeature.SOCKET, () -> socketRuntime.helpers(
                        throwableClasses.indexOf("java/io/IOException"),
                        throwableClasses.indexOf("java/lang/IllegalArgumentException"))),
                new ShimFeatureBlock(ShimFeature.INPUT_STREAMS, shimLibraries::inputStreamHelpers),
                new ShimFeatureBlock(ShimFeature.STRING_FROM_BYTES, shimLibraries::stringFromBytesHelpers)));
        if (usesAny(ShimFeature.HTTPS, ShimFeature.HTTPS_TRUSTED_CERTIFICATE, ShimFeature.SMTP, ShimFeature.SMTP_TLS,
                ShimFeature.POP3)) {
            shim.append(core.tlsClockHelpers());
        }
        if (usesAny(ShimFeature.HTTPS, ShimFeature.HTTPS_TRUSTED_CERTIFICATE, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            if (uses(ShimFeature.HTTPS_TRUSTED_CERTIFICATE)) {
                shim.append(core.httpsTrustHelpers());
            }
            shim.append(core.httpsHelpers(uses(ShimFeature.HTTPS_TRUSTED_CERTIFICATE)));
        }
        if (usesAny(ShimFeature.HTTP, ShimFeature.HTTPS)) {
            shim.append(networkShimLibraries.httpHelpers(features, core.httpClientType(), core.httpsClientType()));
        } else if (uses(ShimFeature.HTTP_TIMEOUT)) {
            shim.append(networkShimLibraries.httpTimeoutStub());
        }
        if (uses(ShimFeature.HTTP_SERVER)) {
            shim.append(networkShimLibraries.httpServerHelpers(
                    uses(ShimFeature.HTTPS_SERVER) ? core.httpsServerTransport() : core.httpServerTransport(),
                    uses(ShimFeature.HTTP_SERVER_HEADERS), uses(ShimFeature.HTTP_SERVER_RESPONSE),
                    uses(ShimFeature.HTTP_SERVER_REQUEST_BODY)));
        }
        if (usesAny(ShimFeature.SMTP, ShimFeature.SMTP_TLS, ShimFeature.POP3)) {
            shim.append(networkShimLibraries.emailHelpers(features, core.httpsClientDeclaration(),
                    core.smtpStarttlsTrust(), core.smtpStarttlsHelpers(), core.smtpStarttlsClient()));
        }
        ShimFeatureBlock.appendUsed(shim, features, List.of(
                new ShimFeatureBlock(ShimFeature.ATOMICS, atomicRuntime::helpers),
                new ShimFeatureBlock(ShimFeature.DURATION, () -> runtimeSource.load("common/duration.cpp")),
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
                    throwableClasses.indexOf("java/util/NoSuchElementException"), carrierCount));
        }
        if (uses(ShimFeature.THREADS)) {
            shim.append(carrierGcRuntime.source(core, carrierCount));
            shim.append(core.carrierRuntime(carrierCount));
            shim.append(core.threadPort(config, carrierCount));
            shim.append(threadRuntime.scheduler(core.preemptsAsynchronously() ? ThreadRuntime.Preemption.TICK
                            : core.asksPortForSlices(carrierCount) ? ThreadRuntime.Preemption.SAFEPOINT_PORT
                            : ThreadRuntime.Preemption.SAFEPOINT_CLOCK,
                    core.maxConcurrentHttpRequests(), core.delayMillisFunction(), "juno_gc_scan_range",
                    uses(ShimFeature.EXCEPTIONS),
                    uses(ShimFeature.THREAD_ENTRY), uses(ShimFeature.TASK_CALLABLE_ENTRY),
                    uses(ShimFeature.STRUCTURED_TASKS), uses(ShimFeature.SCOPED_VALUES),
                    throwableClasses.indexOf("java/util/concurrent/ExecutionException"),
                    throwableClasses.indexOf("java/lang/IllegalStateException"),
                    throwableClasses.indexOf("java/lang/InterruptedException"),
                    throwableClasses.indexOf("java/util/concurrent/BrokenBarrierException"), carrierCount,
                    nativeCalls()));
        } else if (uses(ShimFeature.THREAD_BASICS)) {
            shim.append(threadRuntime.basics(core.delayMillisFunction()));
        }
    }

    /** Whether a blocking library call leaves the runtime: one carrier, a port that allows it, and I/O to make it. */
    private boolean nativeCalls() {
        return uses(ShimFeature.THREADS) && carrierCount == 1 && core.releasesDuringNativeCalls()
                && ioRuntime.needed(features);
    }

    private static Set<ShimFeature> withExceptions(Set<ShimFeature> features) {
        Set<ShimFeature> withExceptions = EnumSet.noneOf(ShimFeature.class);
        withExceptions.addAll(features);
        withExceptions.add(ShimFeature.EXCEPTIONS);
        return withExceptions;
    }
}
