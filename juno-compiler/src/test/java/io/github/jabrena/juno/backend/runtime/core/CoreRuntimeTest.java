package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.ArduinoCore;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class CoreRuntimeTest {
    private final CoreRuntimes coreRuntimes = new CoreRuntimes();

    @Test
    void theRenesasCoreIsCalledDirectlyAndGetsAUsbServicingYield() {
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.RENESAS_UNO);

        assertThat(runtime.delayMillisFunction()).isEqualTo("delay");
        assertThat(runtime.delayMicrosFunction()).isEqualTo("delayMicroseconds");
        assertThat(runtime.wifiIncludes(true)).isEqualTo("#include <WiFiS3.h>\n");
        assertThat(runtime.wifiHelpers()).contains("WiFi.begin(ssid, password)", "WiFi.localIP()");
        assertThat(runtime.udpDeclaration()).isEqualTo("static WiFiUDP juno_udp;");
        assertThat(runtime.httpsInclude()).isEqualTo("#include <WiFiSSLClient.h>\n");
        assertThat(runtime.httpsHelpers()).isEmpty();
        assertThat(runtime.httpsClientDeclaration()).isEqualTo("WiFiSSLClient client;");
        assertThat(runtime.yieldFunction()).contains("extern \"C\" void yield()", "${JUNO_WATCHDOG_REFRESH}")
                .doesNotContain("juno_delay");
    }

    @Test
    void theZephyrCoreKeepsItsOwnYieldAndWrapsItsInlineDelays() {
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.ZEPHYR);

        assertThat(runtime.delayMillisFunction()).isEqualTo("juno_delay");
        assertThat(runtime.delayMicrosFunction()).isEqualTo("juno_delay_microseconds");
        assertThat(runtime.wifiIncludes(false)).isEqualTo("#include <Arduino_RouterBridge.h>\n");
        assertThat(runtime.wifiIncludes(true)).isEqualTo("#include <Arduino_RouterBridge.h>\n");
        assertThat(runtime.wifiHelpers()).contains("Bridge.begin()", "return Bridge ? 3 : 0;")
                .doesNotContain("WiFi.begin");
        assertThat(runtime.udpDeclaration()).isEqualTo("static BridgeUDP<512> juno_udp(Bridge);");
        assertThat(runtime.httpsInclude()).isEmpty();
        assertThat(runtime.httpsHelpers()).contains("class JunoBridgeSSLClient", "connectSSL(host, port, \"\")");
        assertThat(runtime.httpsClientDeclaration()).isEqualTo("JunoBridgeSSLClient client;");
        assertThat(runtime.yieldFunction())
                .contains("extern \"C\" void " + runtime.delayMillisFunction() + "(uint32_t ms)",
                        "extern \"C\" void " + runtime.delayMicrosFunction() + "(uint32_t us)")
                .doesNotContain("void yield()");
    }

    @Test
    void theClassicAvrCoreUsesUnoR3PinsAndHardwareSpi() {
        CoreRuntime runtime = coreRuntimes.of(ArduinoCore.CLASSIC_AVR);

        assertThat(runtime.delayMillisFunction()).isEqualTo("juno_delay_millis");
        assertThat(runtime.delayMicrosFunction()).isEqualTo("juno_delay_micros");
        assertThat(runtime.builtinLedPin()).isEqualTo(13);
        assertThat(runtime.parallelBusHelpers())
                .contains("portOutputRegister(portId)", "digitalPinToBitMask(pin)", "volatile uint8_t*");
        assertThat(runtime.sdHelpers(2, 8, 32, 64, 96))
                .contains("juno_sd.begin(chipSelectPin)")
                .doesNotContain("JunoSdSoftSpiDriver");
        assertThat(runtime.yieldFunction()).isEmpty();
    }

    @Test
    void theThreadPortsDefaultToTheBoardsStackAndTheConfiguredSlots() {
        String renesas = coreRuntimes.of(ArduinoCore.RENESAS_UNO).threadPort(RuntimeConfig.DEFAULT);
        String zephyr = coreRuntimes.of(ArduinoCore.ZEPHYR).threadPort(RuntimeConfig.DEFAULT);

        assertThat(renesas).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = " + RuntimeLimits.MIN_THREAD_STACK_BYTES + "u");
        assertThat(zephyr).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = 4096u");
    }

    @Test
    void theThreadPortsTakeTheConfiguredSlotsAndStack() {
        RuntimeConfig config = new RuntimeConfig(8192, 3, OptionalInt.of(1536));

        assertThat(coreRuntimes.of(ArduinoCore.RENESAS_UNO).threadPort(config))
                .contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 1536u");
        assertThat(coreRuntimes.of(ArduinoCore.ZEPHYR).threadPort(config))
                .contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 1536u");
    }
}
