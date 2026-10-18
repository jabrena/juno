package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.ParallelBusRuntime;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.ArduinoCore;

/** The classic UNO R3's {@code arduino:avr} core for its ATmega328P. */
final class ClassicAvrCoreRuntime implements CoreRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ParallelBusRuntime parallelBusRuntime = new ParallelBusRuntime();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();

    @Override
    public String delayMillisFunction() {
        return "juno_delay_millis";
    }

    @Override
    public String delayMicrosFunction() {
        return "juno_delay_micros";
    }

    @Override
    public int builtinLedPin() {
        return 13;
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    @Override
    public String parallelBusHelpers() {
        return parallelBusRuntime.helpers("",
                "volatile uint8_t*",
                """
                uint8_t portId = digitalPinToPort(pin);
                  port = portOutputRegister(portId);
                  mask = digitalPinToBitMask(pin);""",
                """
                uint8_t setBits = static_cast<uint8_t>(set);
                  uint8_t clearBits = static_cast<uint8_t>(clear);
                  *port = static_cast<uint8_t>((*port & static_cast<uint8_t>(~clearBits)) | setBits);""");
    }

    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes);
    }

    @Override
    public String flashHelpers() {
        return runtimeSource.load("avr/flash.cpp").replace("${JUNO_FLASH_CAPACITY}", "32256");
    }

    @Override
    public String uuidRandomHelpers() {
        throw unsupported("a cryptographic entropy source");
    }

    @Override
    public String wifiIncludes(boolean udp) {
        throw unsupported("networking");
    }

    @Override
    public String wifiHelpers() {
        throw unsupported("networking");
    }

    @Override
    public String httpServerTransport() {
        throw unsupported("an HTTP server");
    }

    @Override
    public String udpDeclaration() {
        throw unsupported("UDP networking");
    }

    @Override
    public String tcpClientHelpers() {
        throw unsupported("TCP networking");
    }

    @Override
    public String httpsInclude() {
        throw unsupported("TLS networking");
    }

    @Override
    public String httpsHelpers() {
        throw unsupported("TLS networking");
    }

    @Override
    public String httpsClientDeclaration() {
        throw unsupported("TLS networking");
    }

    @Override
    public String yieldFunction() {
        return "";
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        int[] timeouts = {15, 30, 60, 120, 250, 500, 1000, 2000, 4000, 8000};
        String[] constants = {"WDTO_15MS", "WDTO_30MS", "WDTO_60MS", "WDTO_120MS", "WDTO_250MS",
                "WDTO_500MS", "WDTO_1S", "WDTO_2S", "WDTO_4S", "WDTO_8S"};
        int selected = selectTimeout(timeoutMillis, timeouts);
        return new WatchdogRuntime("#include <avr/wdt.h>\n", "wdt_enable(" + constants[selected] + ");",
                "wdt_reset();", timeouts[selected]);
    }

    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return threadRuntime.avrPort(config.maxThreads(),
                config.threadStackBytes(ArduinoCore.CLASSIC_AVR.defaultThreadStackBytes()));
    }

    private CompileException unsupported(String feature) {
        return Architecture.AVR.unsupported(feature + " on the classic AVR core");
    }

    private int selectTimeout(int requested, int[] timeouts) {
        for (int index = 0; index < timeouts.length; index++) {
            if (requested > 0 && requested <= timeouts[index]) {
                return index;
            }
        }
        throw new CompileException("@Watchdog timeoutMillis must be between 1 and 8000 on UNO R3: " + requested);
    }
}
