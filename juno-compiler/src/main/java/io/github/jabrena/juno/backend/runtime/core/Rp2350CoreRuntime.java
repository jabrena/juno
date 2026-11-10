package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;

/**
 * The Raspberry Pi Pico 2 W's {@code rp2040:rp2040} core (Earle Philhower's arduino-pico) on its RP2350 Cortex-M33:
 * {@code delay()}/{@code delayMicroseconds()} are real {@code extern "C"} symbols the assembly calls directly, and the
 * core's own weak {@code yield()} serves the generated loop backedges. GPIO, delays, serial and the CYW43439's Wi-Fi
 * (through the core's {@code WiFi} library, whose {@code WiFiClass} mirrors WiFiS3's station and access-point calls)
 * are wired; HTTP, TLS, the watchdog and the thread port are not yet, so the board does not declare those capabilities
 * and the linker rejects them before this runtime is asked.
 */
final class Rp2350CoreRuntime implements CoreRuntime {
    private final ShimLibraries shimLibraries = new ShimLibraries();

    @Override
    public String delayMillisFunction() {
        return "delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "delayMicroseconds";
    }

    /**
     * {@code PIN_LED} of the core's {@code rpipico2w} variant: the LED hangs off the CYW43439's {@code WL_GPIO0}, not an
     * RP2350 GPIO, and the core's CYW43 layer overrides the weak {@code pinMode}/{@code digitalWrite} to route pins 64
     * and above to the radio chip.
     */
    @Override
    public int builtinLedPin() {
        return 64;
    }

    /** {@code A0..A2} are the Pico's three header ADC inputs, GP26..GP28; it has no {@code A3..A5}. */
    @Override
    public String gpioAnalogPins() {
        return "26, 27, 28, -1, -1, -1";
    }

    /**
     * The {@code GPn}/{@code GPIOn} names of the header pins. GP23..GP25 and GP29 are not broken out on the Pico 2 W:
     * the CYW43439 radio uses them.
     */
    @Override
    public String gpioAdditionalDigitalPins() {
        return "0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 26, 27, 28, -1";
    }

    @Override
    public String gpioAdditionalAnalogPins() {
        return "26, 27, 28, -1";
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    /** The core's {@code WiFi.h} also declares {@code WiFiClient}, {@code WiFiServer} and {@code WiFiUDP}. */
    @Override
    public String wifiIncludes(boolean udp) {
        return "#include <WiFi.h>\n";
    }

    /** The shared station and access-point helpers: the core's {@code WiFi} has WiFiS3's {@code begin}/{@code beginAP}. */
    @Override
    public String wifiHelpers() {
        return shimLibraries.wifiHelpers();
    }

    @Override
    public String httpServerTransport() {
        throw unsupported("an HTTP server");
    }

    @Override
    public String udpDeclaration() {
        return "static WiFiUDP juno_udp;";
    }

    @Override
    public String tcpClientHelpers() {
        return "using JunoTcpClient = WiFiClient;\n";
    }

    @Override
    public String httpsInclude() {
        throw unsupported("HTTPS");
    }

    @Override
    public String httpsHelpers() {
        throw unsupported("HTTPS");
    }

    @Override
    public String httpsClientDeclaration() {
        throw unsupported("HTTPS");
    }

    /** The core already defines a weak {@code yield()}; the shim leaves it in place. */
    @Override
    public String yieldFunction() {
        return "// The arduino-pico core provides yield(); the generated assembly's `bl yield` reaches it directly.\n";
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        throw unsupported("@Watchdog");
    }

    @Override
    public String parallelBusHelpers() {
        throw unsupported("ParallelBus");
    }

    /** SdFat on the core's default hardware SPI bus. */
    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes);
    }

    @Override
    public String flashHelpers() {
        throw unsupported("BoardMemory flash figures");
    }

    @Override
    public String uuidRandomHelpers() {
        throw unsupported("UUID.randomUUID()");
    }

    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        throw unsupported("threads");
    }

    private CompileException unsupported(String feature) {
        return Architecture.THUMB2.unsupported(feature + " on the Raspberry Pi Pico 2 W");
    }
}
