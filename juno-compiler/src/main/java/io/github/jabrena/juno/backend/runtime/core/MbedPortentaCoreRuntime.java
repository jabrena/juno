package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.ResolvedRuntimeConfig;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;

/**
 * The Arduino Portenta H7 M7 variant of the Arduino Mbed core. Its standard Arduino delay and GPIO functions are real
 * symbols, so the Thumb-2 backend can use direct calls. The Portenta's built-in RGB LED is exposed by the variant as
 * digital pin 24 ({@code LED_BUILTIN}).
 *
 * <p>The basic Arduino runtime pieces shared with the UNO R4 are inherited. Thread execution uses Mbed OS's RTOS
 * threads rather than the UNO R4's bare-metal stack switcher.
 */
final class MbedPortentaCoreRuntime extends RenesasCoreRuntime implements CoreRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();

    @Override
    public String wifiIncludes(boolean udp) {
        return "#include <WiFi.h>\n";
    }

    @Override
    public String wifiHelpers() {
        // The Mbed Portenta WiFi library requires the password argument even for an open AP.
        return shimLibraries.wifiHelpers().replace("WiFi.beginAP(ssid);", "WiFi.beginAP(ssid, \"\");");
    }

    @Override
    public String i2cBus() {
        // The Portenta variant wires its onboard ATECC608 to the crypto bus exposed as Wire1.
        return "Wire1";
    }

    @Override
    public String httpsServerTransport() {
        return runtimeSource.load("esp32/https-server-transport.cpp");
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        return new WatchdogRuntime("#include <mbed.h>\n",
                "mbed::Watchdog::get_instance().start(" + timeoutMillis + "u);",
                "mbed::Watchdog::get_instance().kick();", timeoutMillis);
    }

    @Override
    public String uuidRandomHelpers() {
        return runtimeSource.load("mbed/uuid-random.cpp");
    }

    @Override
    public String mouseIncludes() {
        return "#include <USBMouse.h>\n";
    }

    @Override
    public String mouseHelpers() {
        return """
                static USBMouse juno_portenta_mouse;
                class JunoPortentaMouse {
                 public:
                  void begin() {}
                  void move(signed char x, signed char y) { juno_portenta_mouse.move(x, y); }
                };
                static JunoPortentaMouse Mouse;
                """;
    }

    @Override
    public boolean preemptsAsynchronously() {
        return false;
    }

    @Override
    public String threadIncludes() {
        return "#include <mbed.h>\n#include <new>\n";
    }

    @Override
    public String threadPort(ResolvedRuntimeConfig config) {
        return threadRuntime.mbedPort(config.threadStackBytes());
    }

    @Override
    public int builtinLedPin() {
        return 24;
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public String gpioFunction(String arduinoFunction) {
        return arduinoFunction.equals("pinMode") ? "juno_pin_mode" : arduinoFunction;
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    @Override
    public String parallelBusHelpers() {
        throw unsupported("ParallelBus");
    }

    @Override
    public String gpioAnalogPins() {
        return "15, 16, 17, 18, 19, 20";
    }

    @Override
    public String yieldFunction() {
        return """
                // The Arduino Mbed core provides yield(); the generated assembly's `bl yield` reaches it directly.
                // Portenta's pinMode overload has C++ linkage, so expose the Arduino-pin overload to the assembly ABI.
                extern "C" void juno_pin_mode(int32_t pin, int32_t mode) {
                  pinMode(static_cast<pin_size_t>(pin), static_cast<PinMode>(mode));
                }
                """;
    }

    private CompileException unsupported(String feature) {
        return Architecture.THUMB2.unsupported(feature + " on the Portenta H7");
    }
}
