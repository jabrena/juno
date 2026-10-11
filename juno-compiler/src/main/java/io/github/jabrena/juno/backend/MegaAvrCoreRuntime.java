package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;

/**
 * The UNO WiFi Rev2's {@code arduino:megaavr} core (ATmega4809). Its {@code delay()} and pin functions take 8- and
 * 16-bit parameters, so the generated code reaches them through the {@code juno_*} wrappers {@link AvrRuntimeShim}
 * defines; its own weak {@code yield()} serves the loop backedges. Networking is the NINA-W102 module through
 * {@code WiFiNINA}: Wi-Fi, UDP and plain HTTP. The board has no LED matrix, no HTTP server or TLS support in Juno, and
 * no thread runtime; the linker's capability checks keep programs needing them from reaching this class.
 */
record MegaAvrCoreRuntime() implements CoreRuntime {
    @Override
    public String delayMillisFunction() {
        return "juno_delay_millis";
    }

    @Override
    public String delayMicrosFunction() {
        return "juno_delay_micros";
    }

    /** {@code LED_BUILTIN} of the {@code uno2018} variant: the "L" LED sits on PD6, digital pin 25, not on D13. */
    @Override
    public int builtinLedPin() {
        return 25;
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    /** megaAVR 0-series ports set and clear bits through their own OUTSET/OUTCLR registers. */
    @Override
    public String parallelBusHelpers() {
        return ParallelBusRuntime.helpers("",
                "PORT_t*",
                """
                port = digitalPinToPortStruct(pin);
                  mask = digitalPinToBitMask(pin);""",
                """
                port->OUTSET = static_cast<uint8_t>(set);
                  port->OUTCLR = static_cast<uint8_t>(clear);""");
    }

    @Override
    public String wifiIncludes(boolean udp) {
        return "#include <WiFiNINA.h>\n" + (udp ? "#include <WiFiUdp.h>\n" : "");
    }

    /** {@code WiFiNINA} has the same {@code WiFi.begin}/{@code beginAP}/{@code status}/{@code localIP} as WiFiS3. */
    @Override
    public String wifiHelpers() {
        return ShimLibraries.wifiHelpers();
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

    /** The core's weak {@code yield()} stays: the board's USB serial is a separate bridge chip, not serviced here. */
    @Override
    public String yieldFunction() {
        return "";
    }

    @Override
    public String threadIncludes() {
        throw unsupported("threads");
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        throw unsupported("threads");
    }

    private static CompileException unsupported(String feature) {
        return AvrAsmBackend.unsupported(feature + " on the megaAVR core");
    }
}
