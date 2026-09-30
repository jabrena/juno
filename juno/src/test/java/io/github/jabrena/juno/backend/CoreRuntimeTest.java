package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.board.ArduinoCore;
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
}
