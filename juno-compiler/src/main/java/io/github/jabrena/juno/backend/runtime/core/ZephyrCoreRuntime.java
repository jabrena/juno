package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.ParallelBusRuntime;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * The UNO Q's {@code arduino:zephyr} core: it provides {@code yield()} itself but inlines
 * {@code delay()}/{@code delayMicroseconds()}, so they are reached through shim wrappers.
 */
final class ZephyrCoreRuntime implements CoreRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ParallelBusRuntime parallelBusRuntime = new ParallelBusRuntime();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();
    private final ShimLibraries shimLibraries = new ShimLibraries();


    @Override
    public String delayMillisFunction() {
        return "juno_delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "juno_delay_microseconds";
    }

    /**
     * PH10, {@code LED3}'s red channel — the first entry of the UNO Q board overlay's
     * {@code builtin-led-gpios} and the pin {@code LED_BUILTIN} resolves to in the Zephyr core; D13
     * on this board's header has no LED wired to it. Position within {@code digital-pin-gpios} in
     * {@code arduino_uno_q_stm32u585xx.overlay}, hardware-confirmed 2026-09-30.
     */
    @Override
    public int builtinLedPin() {
        return 50;
    }

    /** {@code Arduino_LED_Matrix.h} of the zephyr core declares {@code Arduino_LED_Matrix}, 13x8. */
    @Override
    public String ledMatrixType() {
        return "Arduino_LED_Matrix";
    }

    @Override
    public int ledMatrixColumns() {
        return 13;
    }

    /**
     * The same devicetree pin table the core's {@code digitalWrite} indexes, written through the GPIO driver's
     * raw port API: one call sets and clears any of a port's bits.
     */
    @Override
    public String parallelBusHelpers() {
        return parallelBusRuntime.helpers("""
                #include <zephyr/drivers/gpio.h>
                static const struct gpio_dt_spec juno_bus_pin_table[] = {
                    DT_FOREACH_PROP_ELEM_SEP(DT_PATH(zephyr_user), digital_pin_gpios, GPIO_DT_SPEC_GET_BY_IDX, (, ))};""",
                "const struct device*",
                """
                port = juno_bus_pin_table[pin].port;
                  mask = 1u << juno_bus_pin_table[pin].pin;""",
                "gpio_port_set_clr_bits_raw(port, set, clear);");
    }

    @Override
    public String wifiIncludes(boolean udp) {
        // UNO Q networking belongs to its Linux MPU. Arduino_RouterBridge carries UDP and other
        // network operations between the Zephyr sketch on the MCU and arduino-router on Linux.
        return "#include <Arduino_RouterBridge.h>\n";
    }

    /** SdFat on the core's default hardware SPI bus. */
    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes);
    }

    @Override
    public String wifiHelpers() {
        return runtimeSource.load("zephyr/wifi.cpp");
    }

    @Override
    public String flashHelpers() {
        return runtimeSource.load("zephyr/flash.cpp");
    }

    @Override
    public String uuidRandomHelpers() {
        return runtimeSource.load("zephyr/uuid-random.cpp");
    }

    @Override
    public String httpServerTransport() {
        // The listener lives in arduino-router on Linux (tcp/listen, tcp/accept). BridgeTCPServer
        // keeps returning the same connection from accept() until disconnect() is called, so
        // release() must run once the response has been sent.
        return runtimeSource.load("zephyr/http-server-transport.cpp");
    }

    @Override
    public String httpsServerTransport() {
        // The router only listens for plain TCP, so the TLS server session runs on the MCU with BearSSL (the JunoBearSSL
        // library from juno-third-parties) over the same bridge connection.
        return runtimeSource.load("zephyr/https-server-transport.cpp");
    }

    @Override
    public String udpDeclaration() {
        // Keep the bridge buffer bounded; Juno's UDP payloads are caller-sized and streamed over RPC.
        return "static BridgeUDP<512> juno_udp(Bridge);";
    }

    @Override
    public String tcpClientHelpers() {
        return runtimeSource.load("zephyr/tcp.cpp");
    }

    @Override
    public String httpsInclude() {
        return "";
    }

    @Override
    public String httpsHelpers() {
        return runtimeSource.load("zephyr/https.cpp");
    }

    @Override
    public String httpsClientDeclaration() {
        return "JunoBridgeSSLClient client;";
    }

    @Override
    public String yieldFunction() {
        return runtimeSource.load("zephyr/yield.cpp");
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        throw new UnsupportedOperationException("the Zephyr firmware does not enable its watchdog subsystem");
    }

    @Override
    public boolean ioWorker() {
        return true;
    }

    @Override
    public String ioWorkerPort() {
        return threadRuntime.zephyrIoWorker();
    }

    @Override
    public String threadIncludes() {
        return "#include <zephyr/kernel.h>\n";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return threadRuntime.zephyrPort(config.maxThreads(), config.threadStackBytes(ArduinoCore.ZEPHYR.defaultThreadStackBytes()));
    }
}
