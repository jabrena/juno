package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.board.ArduinoCore;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreRuntimeTest {
    @Test
    void theRenesasCoreIsCalledDirectlyAndGetsAUsbServicingYield() {
        CoreRuntime runtime = CoreRuntime.of(ArduinoCore.RENESAS_UNO);

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
        CoreRuntime runtime = CoreRuntime.of(ArduinoCore.ZEPHYR);

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
    void theThreadPortsDefaultToTheBoardsStackAndTheConfiguredSlots() {
        String renesas = CoreRuntime.of(ArduinoCore.RENESAS_UNO).threadPort(RuntimeConfig.DEFAULT);
        String zephyr = CoreRuntime.of(ArduinoCore.ZEPHYR).threadPort(RuntimeConfig.DEFAULT);

        assertThat(renesas).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = " + RuntimeLimits.MIN_THREAD_STACK_BYTES + "u");
        assertThat(zephyr).contains("JUNO_MAX_THREADS = " + RuntimeLimits.MAX_THREADS + "u",
                "JUNO_THREAD_STACK_BYTES = 4096u");
    }

    @Test
    void theThreadPortsTakeTheConfiguredSlotsAndStack() {
        RuntimeConfig config = new RuntimeConfig(8192, 3, OptionalInt.of(1536));

        assertThat(CoreRuntime.of(ArduinoCore.RENESAS_UNO).threadPort(config))
                .contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 1536u");
        assertThat(CoreRuntime.of(ArduinoCore.ZEPHYR).threadPort(config))
                .contains("JUNO_MAX_THREADS = 3u", "JUNO_THREAD_STACK_BYTES = 1536u");
    }
}
