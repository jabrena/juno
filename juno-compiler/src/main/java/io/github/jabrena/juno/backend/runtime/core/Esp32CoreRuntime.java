package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.ArduinoCore;

import java.util.Map;

/**
 * The Nano ESP32's {@code arduino:esp32} core. {@code delay()}/{@code delayMicroseconds()} and {@code yield()} are
 * plain C symbols the assembly calls directly. The board builds with {@code BOARD_HAS_PIN_REMAP}, which turns
 * {@code pinMode}/{@code digitalWrite} and friends into macros translating the printed pin number ({@code D13}) to
 * the ESP32-S3 GPIO number (48); the real symbols take GPIO numbers, so the assembly reaches them through
 * {@code juno_*} wrappers compiled with the macros in effect. Threads are FreeRTOS tasks handing a baton (see
 * {@link ThreadRuntime#esp32Port}). SD cards use the shared SdFat helpers over the core's default SPI pins. Everything
 * else beyond GPIO, delays, serial, threads, SD cards and station-mode Wi-Fi is not wired to this core yet and fails at
 * compile time.
 */
final class Esp32CoreRuntime implements CoreRuntime {
    private static final Map<String, String> PIN_REMAP_WRAPPERS = Map.of(
            "pinMode", "juno_pin_mode",
            "digitalWrite", "juno_digital_write",
            "digitalRead", "juno_digital_read",
            "analogRead", "juno_analog_read",
            "analogWrite", "juno_analog_write");

    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();

    @Override
    public String delayMillisFunction() {
        return "delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "delayMicroseconds";
    }

    @Override
    public String gpioFunction(String arduinoFunction) {
        return PIN_REMAP_WRAPPERS.getOrDefault(arduinoFunction, arduinoFunction);
    }

    /** {@code LED_BUILTIN} under the core's default "By Arduino pin" numbering. */
    @Override
    public int builtinLedPin() {
        return 13;
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    /** {@code WiFi.h} covers the station and access-point calls; the UDP and TCP classes are not wired yet. */
    @Override
    public String wifiIncludes(boolean udp) {
        if (udp) {
            throw unsupported("UDP networking");
        }
        return "#include <WiFi.h>\n";
    }

    /** Station and access-point helpers over the core's {@code WiFi} class ({@code softAP} for the access point). */
    @Override
    public String wifiHelpers() {
        return runtimeSource.load("esp32/wifi.cpp");
    }

    /** A {@code WiFiServer} on the station interface; the shared request parser reads its accepted clients. */
    @Override
    public String httpServerTransport() {
        return runtimeSource.load("esp32/http-server-transport.cpp");
    }

    @Override
    public String udpDeclaration() {
        throw unsupported("UDP networking");
    }

    @Override
    public String httpsInclude() {
        return "#include <WiFiClientSecure.h>\n";
    }

    /**
     * The TLS client skips certificate-chain validation, as the shim's STARTTLS wrapper already does; the core's
     * embedded CA bundle would need an SDK symbol this core does not ship with, so the chain is not checked yet.
     */
    @Override
    public String httpsHelpers() {
        return """
                struct JunoEspTlsClient : WiFiClientSecure {
                  JunoEspTlsClient() { setInsecure(); }
                };
                """;
    }

    @Override
    public String httpsClientDeclaration() {
        return "JunoEspTlsClient client;";
    }

    @Override
    public String yieldFunction() {
        return runtimeSource.load("esp32/core-glue.cpp");
    }

    @Override
    public String watchdogYieldFunction() {
        return yieldFunction() + runtimeSource.load("esp32/watchdog-yield.cpp");
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        int timeoutSeconds = Math.ceilDiv(timeoutMillis, 1000);
        return new WatchdogRuntime("#include <esp_task_wdt.h>\n",
                "esp_task_wdt_init(" + timeoutSeconds + "u, true);\nesp_task_wdt_add(nullptr);",
                "esp_task_wdt_reset();", Math.multiplyExact(timeoutSeconds, 1000));
    }

    @Override
    public String parallelBusHelpers() {
        throw unsupported("ParallelBus");
    }

    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes,
                "", "juno_sd.begin(juno_nano_pin(chipSelectPin))");
    }

    @Override
    public String flashHelpers() {
        throw unsupported("flash usage queries");
    }

    @Override
    public String uuidRandomHelpers() {
        throw unsupported("a cryptographic entropy source");
    }

    @Override
    public String threadIncludes() {
        return "#include <freertos/FreeRTOS.h>\n#include <freertos/semphr.h>\n#include <freertos/task.h>\n";
    }

    /** FreeRTOS tasks carry the stacks; the backend preempts at safepoints rather than from a tick. */
    @Override
    public String threadPort(RuntimeConfig config) {
        return threadRuntime.esp32Port(config.maxThreads(),
                config.threadStackBytes(ArduinoCore.ESP32.defaultThreadStackBytes()));
    }

    @Override
    public boolean preemptsAsynchronously() {
        return false;
    }

    private CompileException unsupported(String feature) {
        return Architecture.XTENSA.unsupported(feature + " on the ESP32 core");
    }
}
