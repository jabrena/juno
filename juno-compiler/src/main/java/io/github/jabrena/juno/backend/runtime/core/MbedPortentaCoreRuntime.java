package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.Architecture;

/**
 * The Arduino Portenta H7 M7 variant of the Arduino Mbed core. Its standard Arduino delay and GPIO functions are real
 * symbols, so the Thumb-2 backend can use direct calls. The Portenta's built-in RGB LED is exposed by the variant as
 * digital pin 24 ({@code LED_BUILTIN}).
 *
 * <p>The basic runtime pieces shared with the UNO R4 are inherited; optional services remain unavailable until they
 * have a Portenta-specific implementation and board capability declaration.
 */
final class MbedPortentaCoreRuntime extends RenesasCoreRuntime implements CoreRuntime {
    private final ShimLibraries shimLibraries = new ShimLibraries();

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
    public String wifiIncludes(boolean udp) {
        return "#include <WiFi.h>\n";
    }

    @Override
    public String wifiHelpers() {
        // The Mbed Portenta WiFi library has no one-argument beginAP overload; keep the shared helper's open-network
        // behavior by passing an empty passphrase explicitly.
        return shimLibraries.wifiHelpers().replace("WiFi.beginAP(ssid);", "WiFi.beginAP(ssid, \"\");");
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
