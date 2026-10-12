package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.ParallelBusRuntime;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * The UNO R4's {@code arduino:renesas_uno} core: {@code delay()}/{@code delayMicroseconds()} are real
 * symbols the assembly calls directly, and the shim overrides the core's weak {@code yield()} to keep
 * USB serviced.
 */
record RenesasCoreRuntime() implements CoreRuntime {

    @Override
    public String delayMillisFunction() {
        return "delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "delayMicroseconds";
    }

    @Override
    public int builtinLedPin() {
        return 13;
    }

    /** {@code Arduino_LED_Matrix.h} of the renesas_uno core declares {@code ArduinoLEDMatrix}, 12x8. */
    @Override
    public String ledMatrixType() {
        return "ArduinoLEDMatrix";
    }

    @Override
    public int ledMatrixColumns() {
        return 12;
    }

    /**
     * A pin's {@code g_pin_cfg} entry holds its RA port (high byte) and bit (low byte); each port's {@code PCNTR3}
     * sets its low half's bits and resets its high half's in one store.
     */
    @Override
    public String parallelBusHelpers() {
        return ParallelBusRuntime.helpers("",
                "R_PORT0_Type*",
                """
                uint32_t io = static_cast<uint32_t>(g_pin_cfg[pin].pin);
                  port = reinterpret_cast<R_PORT0_Type*>(R_PORT0_BASE + (R_PORT1_BASE - R_PORT0_BASE) * (io >> 8));
                  mask = 1u << (io & 0xFFu);""",
                "port->PCNTR3 = (clear << 16) | set;");
    }

    @Override
    public String wifiIncludes(boolean udp) {
        // WiFiS3.h exposes WiFi, WiFiClient, WiFiServer, WiFiSSLClient and WiFiUDP.
        return "#include <WiFiS3.h>\n";
    }

    /** SdFat on the core's default hardware SPI bus. */
    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return ShimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes);
    }

    @Override
    public String wifiHelpers() {
        return ShimLibraries.wifiHelpers();
    }

    @Override
    public String httpServerTransport() {
        return RuntimeSource.load("renesas/http-server-transport.cpp");
    }

    @Override
    public String udpDeclaration() {
        return "static WiFiUDP juno_udp;";
    }

    @Override
    public String httpsInclude() {
        return "#include <WiFiSSLClient.h>\n";
    }

    @Override
    public String httpsHelpers() {
        return "";
    }

    @Override
    public String httpsClientDeclaration() {
        return "WiFiSSLClient client;";
    }

    @Override
    public String yieldFunction() {
        return RuntimeSource.load("renesas/yield.cpp");
    }

    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return ThreadRuntime.renesasPort(config.maxThreads(),
                config.threadStackBytes(ArduinoCore.RENESAS_UNO.defaultThreadStackBytes()));
    }
}
