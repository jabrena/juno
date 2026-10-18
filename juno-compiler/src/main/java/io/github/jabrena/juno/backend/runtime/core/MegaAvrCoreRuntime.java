package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.AvrRuntimeShim;
import io.github.jabrena.juno.backend.runtime.ParallelBusRuntime;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
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
final class MegaAvrCoreRuntime implements CoreRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ParallelBusRuntime parallelBusRuntime = new ParallelBusRuntime();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();
    private final ShimLibraries shimLibraries = new ShimLibraries();

    @Override
    public String delayMillisFunction() {
        return "juno_delay_millis";
    }

    @Override
    public String delayMicrosFunction() {
        return "juno_delay_micros";
    }

    @Override
    public String avrPinModeType() {
        return "PinMode";
    }

    @Override
    public String avrPinStatusType() {
        return "PinStatus";
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
        return parallelBusRuntime.helpers("",
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
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes,
                runtimeSource.load("megaavr/sd-soft-spi.cpp"),
                "juno_sd.begin(SdSpiConfig(chipSelectPin, SHARED_SPI, SD_SCK_MHZ(0), &juno_sd_spi))");
    }

    @Override
    public String flashHelpers() {
        return runtimeSource.load("avr/flash.cpp").replace("${JUNO_FLASH_CAPACITY}", "48640");
    }

    @Override
    public String uuidRandomHelpers() {
        return runtimeSource.load("megaavr/uuid-random.cpp");
    }

    @Override
    public String wifiIncludes(boolean udp) {
        return "#include <WiFiNINA.h>\n" + (udp ? "#include <WiFiUdp.h>\n" : "");
    }

    /** {@code WiFiNINA} has the same {@code WiFi.begin}/{@code beginAP}/{@code status}/{@code localIP} as WiFiS3. */
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

    /** {@code WiFiNINA.h} already declares {@code WiFiSSLClient}. */
    @Override
    public String httpsInclude() {
        return "";
    }

    @Override
    public String httpsHelpers() {
        return "";
    }

    /** Keep NINA TLS handshakes from blocking another request's receive deadline. */
    @Override
    public int maxConcurrentHttpRequests() {
        return 1;
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

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        int[] timeouts = {8, 16, 32, 64, 128, 256, 512, 1000, 2000, 4100, 8200};
        String[] constants = {"WDT_PERIOD_8CLK_gc", "WDT_PERIOD_16CLK_gc", "WDT_PERIOD_32CLK_gc",
                "WDT_PERIOD_64CLK_gc", "WDT_PERIOD_128CLK_gc", "WDT_PERIOD_256CLK_gc",
                "WDT_PERIOD_512CLK_gc", "WDT_PERIOD_1KCLK_gc", "WDT_PERIOD_2KCLK_gc",
                "WDT_PERIOD_4KCLK_gc", "WDT_PERIOD_8KCLK_gc"};
        int selected = selectTimeout(timeoutMillis, timeouts);
        return new WatchdogRuntime("#include <avr/io.h>\n",
                "_PROTECTED_WRITE(WDT.CTRLA, " + constants[selected] + ");",
                "__asm__ __volatile__(\"wdr\");", timeouts[selected]);
    }

    /** The AVR port needs nothing beyond {@code <string.h>}, which every shim includes. */
    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return threadRuntime.avrPort(config.maxThreads(),
                config.threadStackBytes(ArduinoCore.MEGAAVR.defaultThreadStackBytes()));
    }

    private CompileException unsupported(String feature) {
        return Architecture.AVR.unsupported(feature + " on the megaAVR core");
    }

    private int selectTimeout(int requested, int[] timeouts) {
        for (int index = 0; index < timeouts.length; index++) {
            if (requested > 0 && requested <= timeouts[index]) {
                return index;
            }
        }
        throw new CompileException(
                "@Watchdog timeoutMillis must be between 1 and 8200 on UNO WiFi Rev2: " + requested);
    }
}
