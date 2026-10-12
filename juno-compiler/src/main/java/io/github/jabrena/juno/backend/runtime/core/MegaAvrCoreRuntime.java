package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.AvrRuntimeShim;
import io.github.jabrena.juno.backend.runtime.ParallelBusRuntime;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * The UNO WiFi Rev2's {@code arduino:megaavr} core (ATmega4809). Its {@code delay()} and pin functions take 8- and
 * 16-bit parameters, so the generated code reaches them through the {@code juno_*} wrappers {@link AvrRuntimeShim}
 * defines; its own weak {@code yield()} serves the loop backedges. Networking is the NINA-W102 module through
 * {@code WiFiNINA}: Wi-Fi, UDP, HTTP, HTTPS and the implicit-TLS email clients (TLS on the module). Tasks run on the
 * preemptive scheduler over {@link ThreadRuntime#avrPort}, preempted at safepoints. The board has no LED matrix, no HTTP server and no
 * STARTTLS mail submission; the linker's capability checks keep programs needing them from reaching this class.
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

    /**
     * The Rev2 routes its hardware SPI bus to the ICSP header, while classic UNO shields wire SD to D11-D13. Keep
     * WiFiNINA on hardware SPI and bit-bang the shield's SD bus instead.
     */
    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return ShimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes,
                RuntimeSource.load("megaavr/sd-soft-spi.cpp"),
                "juno_sd.begin(SdSpiConfig(chipSelectPin, SHARED_SPI, SD_SCK_MHZ(0), &juno_sd_spi))");
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

    /** {@code WiFiNINA.h} already declares {@code WiFiSSLClient}. */
    @Override
    public String httpsInclude() {
        return "";
    }

    @Override
    public String httpsHelpers() {
        return "";
    }

    /**
     * The NINA-W102 module runs TLS itself, so HTTPS costs the ATmega4809 next to nothing; the module trusts the root
     * certificates in its firmware (the Arduino IDE's certificate uploader adds more).
     */
    @Override
    public String httpsClientDeclaration() {
        return "WiFiSSLClient client;";
    }

    /** The core's weak {@code yield()} stays: the board's USB serial is a separate bridge chip, not serviced here. */
    @Override
    public String yieldFunction() {
        return "";
    }

    /** The AVR port needs nothing beyond {@code <string.h>}, which every shim includes. */
    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return ThreadRuntime.avrPort(config.maxThreads(),
                config.threadStackBytes(ArduinoCore.MEGAAVR.defaultThreadStackBytes()));
    }

    private static CompileException unsupported(String feature) {
        return Architecture.AVR.unsupported(feature + " on the megaAVR core");
    }
}
