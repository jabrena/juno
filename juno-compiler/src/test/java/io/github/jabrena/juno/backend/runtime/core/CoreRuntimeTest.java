package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.ArduinoCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Per-core runtime implementations")
class CoreRuntimeTest {
    private final CoreRuntimes coreRuntimes = new CoreRuntimes();

    @Test
    @DisplayName("The Renesas core calls delay directly")
    void shouldCallTheRenesasDelayFunctionsDirectly() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(new String[] {runtime.delayMillisFunction(), runtime.delayMicrosFunction()})
                .containsExactly("delay", "delayMicroseconds");
    }

    @Test
    @DisplayName("The Renesas core includes WiFiS3 for Wi-Fi")
    void shouldIncludeWiFiS3ForTheRenesasCore() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(runtime.wifiIncludes(true)).isEqualTo("#include <WiFiS3.h>\n");
    }

    @Test
    @DisplayName("The Renesas core provides the WiFi begin and local IP helpers")
    void shouldProvideTheRenesasWifiHelpers() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(runtime.wifiHelpers()).contains("WiFi.begin(ssid, password)", "WiFi.localIP()");
    }

    @Test
    @DisplayName("The Renesas core declares its UDP socket and SSL client")
    void shouldDeclareTheRenesasUdpSocketAndSslClient() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(new String[] {
                runtime.udpDeclaration(), runtime.httpsInclude(), runtime.httpsClientDeclaration()})
                .containsExactly("static WiFiUDP juno_udp;", "#include <WiFiSSLClient.h>\n", "WiFiSSLClient client;");
    }

    @Test
    @DisplayName("The Renesas core needs no SSL helper code")
    void shouldNeedNoSslHelpersOnTheRenesasCore() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(runtime.httpsHelpers()).isEmpty();
    }

    @Test
    @DisplayName("The Renesas core gets a USB-servicing yield")
    void shouldGiveTheRenesasCoreAUsbServicingYield() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(runtime.yieldFunction()).contains("extern \"C\" void yield()", "${JUNO_WATCHDOG_REFRESH}")
                .doesNotContain("juno_delay");
    }

    @Test
    @DisplayName("The Zephyr core keeps its own delay wrappers")
    void shouldKeepItsOwnDelayWrappersOnZephyr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(new String[] {runtime.delayMillisFunction(), runtime.delayMicrosFunction()})
                .containsExactly("juno_delay", "juno_delay_microseconds");
    }

    @Test
    @DisplayName("The Zephyr core uses the Router Bridge for Wi-Fi either way")
    void shouldUseTheRouterBridgeOnZephyrForWifi() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(new String[] {runtime.wifiIncludes(false), runtime.wifiIncludes(true)})
                .containsOnly("#include <Arduino_RouterBridge.h>\n");
    }

    @Test
    @DisplayName("The Zephyr core provides Bridge helpers without WiFi.begin")
    void shouldProvideBridgeWifiHelpersWithoutWifiBegin() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(runtime.wifiHelpers()).contains("Bridge.begin()", "return Bridge ? 3 : 0;")
                .doesNotContain("WiFi.begin");
    }

    @Test
    @DisplayName("The Zephyr core declares a Bridge UDP socket and SSL client")
    void shouldDeclareTheBridgeUdpSocketAndSslClientOnZephyr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(new String[] {runtime.udpDeclaration(), runtime.httpsClientDeclaration()})
                .containsExactly("static BridgeUDP<512> juno_udp(Bridge);", "JunoBridgeSSLClient client;");
    }

    @Test
    @DisplayName("The Zephyr core needs no HTTPS include")
    void shouldNeedNoHttpsIncludeOnZephyr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(runtime.httpsInclude()).isEmpty();
    }

    @Test
    @DisplayName("The Zephyr core provides the Bridge SSL client helpers")
    void shouldProvideTheBridgeSslClientHelpersOnZephyr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(runtime.httpsHelpers()).contains("class JunoBridgeSSLClient", "connectSSL(host, port, \"\")");
    }

    @Test
    @DisplayName("The Zephyr core wraps its delays in the yield function")
    void shouldWrapTheZephyrDelaysInItsYieldFunction() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        // When & Then
        assertThat(runtime.yieldFunction())
                .contains("extern \"C\" void " + runtime.delayMillisFunction() + "(uint32_t ms)",
                        "extern \"C\" void " + runtime.delayMicrosFunction() + "(uint32_t us)")
                .doesNotContain("void yield()");
    }

    @Test
    @DisplayName("The classic AVR core exposes its delay functions")
    void shouldExposeTheClassicAvrDelayFunctions() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.CLASSIC_AVR);

        // When & Then
        assertThat(new String[] {runtime.delayMillisFunction(), runtime.delayMicrosFunction()})
                .containsExactly("juno_delay_millis", "juno_delay_micros");
    }

    @Test
    @DisplayName("The classic AVR core uses the UNO R3 built-in LED on pin 13")
    void shouldUseTheUnoR3BuiltinLedOnPin13() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.CLASSIC_AVR);

        // When & Then
        assertThat(runtime.builtinLedPin()).isEqualTo(13);
    }

    @Test
    @DisplayName("The classic AVR core drives the parallel bus through port registers")
    void shouldDriveTheParallelBusThroughPortRegistersOnAvr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.CLASSIC_AVR);

        // When & Then
        assertThat(runtime.parallelBusHelpers())
                .contains("portOutputRegister(portId)", "digitalPinToBitMask(pin)", "volatile uint8_t*");
    }

    @Test
    @DisplayName("The classic AVR core uses the hardware SPI SD driver")
    void shouldUseTheHardwareSpiSdDriverOnAvr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.CLASSIC_AVR);

        // When & Then
        assertThat(runtime.sdHelpers(2, 8, 32, 64, 96))
                .contains("juno_sd.begin(chipSelectPin)")
                .doesNotContain("JunoSdSoftSpiDriver");
    }

    @Test
    @DisplayName("The classic AVR core has no yield function")
    void shouldHaveNoYieldFunctionOnAvr() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.CLASSIC_AVR);

        // When & Then
        assertThat(runtime.yieldFunction()).isEmpty();
    }

    @Test
    @DisplayName("The classic AVR core has its own watchdog sequence")
    void shouldProvideTheClassicAvrWatchdogSequence() {
        assertThat(coreRuntimes.of(ArduinoCore.CLASSIC_AVR).watchdog(3000))
                .extracting("includes", "begin", "refresh", "effectiveTimeoutMillis")
                .containsExactly("#include <avr/wdt.h>\n", "wdt_enable(WDTO_4S);", "wdt_reset();", 4000);
    }

    @Test
    @DisplayName("The megaAVR core has its own watchdog sequence")
    void shouldProvideTheMegaAvrWatchdogSequence() {
        assertThat(coreRuntimes.of(ArduinoCore.MEGAAVR).watchdog(3000))
                .extracting("includes", "begin", "refresh", "effectiveTimeoutMillis")
                .containsExactly("#include <avr/io.h>\n",
                        "_PROTECTED_WRITE(WDT.CTRLA, WDT_PERIOD_4KCLK_gc);",
                        "__asm__ __volatile__(\"wdr\");", 4100);
    }

    @Test
    @DisplayName("The Renesas core has its own watchdog sequence")
    void shouldProvideTheRenesasWatchdogSequence() {
        assertThat(coreRuntimes.of(ArduinoCore.RENESAS_UNO).watchdog(3000))
                .extracting("includes", "begin", "refresh", "effectiveTimeoutMillis")
                .containsExactly("#include <WDT.h>\n", "WDT.begin(3000u);", "WDT.refresh();", 3000);
    }

    @Test
    @DisplayName("The Zephyr core rejects a watchdog because its subsystem is not enabled")
    void shouldRejectAWatchdogOnZephyr() {
        // When & Then
        assertThatThrownBy(() -> coreRuntimes.of(ArduinoCore.ZEPHYR).watchdog(3000))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Zephyr firmware does not enable its watchdog subsystem");
    }

    @Test
    @DisplayName("The Renesas thread port sizes its slots and stack from the defaults")
    void shouldSizeTheRenesasThreadPortFromTheDefaults() {
        // When
        String renesas = coreRuntimes.of(ArduinoCore.RENESAS_UNO).threadPort(RuntimeConfig.DEFAULT);

        // Then
        assertThat(renesas).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = " + RuntimeLimits.MIN_THREAD_STACK_BYTES + "u");
    }

    @Test
    @DisplayName("The Zephyr thread port sizes its slots and stack from the defaults")
    void shouldSizeTheZephyrThreadPortFromTheDefaults() {
        // When
        String zephyr = coreRuntimes.of(ArduinoCore.ZEPHYR).threadPort(RuntimeConfig.DEFAULT);

        // Then
        assertThat(zephyr).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = 4096u");
    }

    @Test
    @DisplayName("The Renesas thread port takes the configured slots and stack")
    void shouldApplyTheConfiguredSlotsAndStackToTheRenesasThreadPort() {
        // Given
        RuntimeConfig config = new RuntimeConfig(8192, 3, OptionalInt.of(1536));

        // When
        String renesas = coreRuntimes.of(ArduinoCore.RENESAS_UNO).threadPort(config);

        // Then
        assertThat(renesas).contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 1536u");
    }

    @Test
    @DisplayName("The Zephyr thread port takes the configured slots and stack")
    void shouldApplyTheConfiguredSlotsAndStackToTheZephyrThreadPort() {
        // Given
        RuntimeConfig config = new RuntimeConfig(8192, 3, OptionalInt.of(1536));

        // When
        String zephyr = coreRuntimes.of(ArduinoCore.ZEPHYR).threadPort(config);

        // Then
        assertThat(zephyr).contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 1536u");
    }

    static Stream<Arguments> unsupportedEsp32Features() {
        return Stream.of(
                esp32Feature("an LED matrix", CoreRuntime::ledMatrixType),
                esp32Feature("an LED matrix", CoreRuntime::ledMatrixColumns),
                esp32Feature("networking", runtime -> runtime.wifiIncludes(false)),
                esp32Feature("networking", CoreRuntime::wifiHelpers),
                esp32Feature("an HTTP server", CoreRuntime::httpServerTransport),
                esp32Feature("UDP networking", CoreRuntime::udpDeclaration),
                esp32Feature("TLS networking", CoreRuntime::httpsInclude),
                esp32Feature("TLS networking", CoreRuntime::httpsHelpers),
                esp32Feature("TLS networking", CoreRuntime::httpsClientDeclaration),
                esp32Feature("@Watchdog", runtime -> runtime.watchdog(1000)),
                esp32Feature("ParallelBus", CoreRuntime::parallelBusHelpers),
                esp32Feature("SD cards", runtime -> runtime.sdHelpers(1, 1, 1, 1, 1)),
                esp32Feature("flash usage queries", CoreRuntime::flashHelpers),
                esp32Feature("a cryptographic entropy source", CoreRuntime::uuidRandomHelpers));
    }

    @Test
    @DisplayName("The ESP32 thread port is FreeRTOS tasks with an 8 KiB default stack, preempted at safepoints")
    void shouldPortThreadsToFreeRtosTasksOnTheEsp32() {
        // Given
        CoreRuntime esp32 = coreRuntimes.of(ArduinoCore.ESP32);

        // When
        String port = esp32.threadPort(RuntimeConfig.DEFAULT);

        // Then
        assertThat(esp32.threadIncludes()).contains("#include <freertos/semphr.h>", "#include <freertos/task.h>");
        assertThat(port).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = 8192u", "xTaskCreateStaticPinnedToCore", "xSemaphoreTake");
        assertThat(esp32.preemptsAsynchronously()).isFalse();
    }

    @Test
    @DisplayName("The ESP32 thread port takes the configured slots and stack")
    void shouldApplyTheConfiguredSlotsAndStackToTheEsp32ThreadPort() {
        // Given
        RuntimeConfig config = new RuntimeConfig(8192, 3, OptionalInt.of(4096));

        // When
        String port = coreRuntimes.of(ArduinoCore.ESP32).threadPort(config);

        // Then
        assertThat(port).contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 4096u");
    }

    private static Arguments esp32Feature(String feature, Consumer<CoreRuntime> use) {
        return arguments(feature, use);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedEsp32Features")
    @DisplayName("The ESP32 core rejects what it does not wire yet")
    void shouldRejectWhatTheEsp32CoreDoesNotWireYet(String feature, Consumer<CoreRuntime> use) {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ESP32);

        // When & Then
        assertThatThrownBy(() -> use.accept(runtime))
                .hasMessage("The Xtensa assembly backend (Nano ESP32) does not support this yet: " + feature
                        + " on the ESP32 core");
    }

    @Test
    @DisplayName("The ESP32 core calls the delays directly and the GPIO functions through pin-remap wrappers")
    void shouldReachTheEsp32GpioThroughPinRemapWrappers() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ESP32);

        // When & Then
        assertThat(new String[] {runtime.delayMillisFunction(), runtime.delayMicrosFunction(),
                runtime.gpioFunction("pinMode"), runtime.gpioFunction("analogWrite"), runtime.gpioFunction("millis"),
                Integer.toString(runtime.builtinLedPin())})
                .containsExactly("delay", "delayMicroseconds", "juno_pin_mode", "juno_analog_write", "millis", "13");
    }

    @Test
    @DisplayName("Cores without pin remapping call the Arduino GPIO functions by name")
    void shouldCallTheGpioFunctionsByNameWithoutPinRemapping() {
        // Given
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        // When & Then
        assertThat(runtime.gpioFunction("digitalWrite")).isEqualTo("digitalWrite");
    }
}
