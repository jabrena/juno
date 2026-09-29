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
        assertThat(runtime.yieldFunction()).contains("extern \"C\" void yield()", "${JUNO_WATCHDOG_REFRESH}")
                .doesNotContain("juno_delay");
    }

    @Test
    void theZephyrCoreKeepsItsOwnYieldAndWrapsItsInlineDelays() {
        CoreRuntime runtime = CoreRuntime.of(ArduinoCore.ZEPHYR);

        assertThat(runtime.delayMillisFunction()).isEqualTo("juno_delay");
        assertThat(runtime.delayMicrosFunction()).isEqualTo("juno_delay_microseconds");
        assertThat(runtime.wifiIncludes(false)).isEqualTo("#include <WiFi.h>\n");
        assertThat(runtime.wifiIncludes(true)).contains("#include <WiFi.h>", "#include <WiFiUdp.h>");
        assertThat(runtime.yieldFunction())
                .contains("extern \"C\" void " + runtime.delayMillisFunction() + "(uint32_t ms)",
                        "extern \"C\" void " + runtime.delayMicrosFunction() + "(uint32_t us)")
                .doesNotContain("void yield()");
    }
}
