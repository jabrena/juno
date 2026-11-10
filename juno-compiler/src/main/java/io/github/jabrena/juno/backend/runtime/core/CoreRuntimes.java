package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.ArduinoCore;

/** The one place that names a {@link CoreRuntime} implementation: each {@link ArduinoCore} maps to exactly one. */
public final class CoreRuntimes {

    public CoreRuntime of(ArduinoCore core) {
        return switch (core) {
            case CLASSIC_AVR -> new ClassicAvrCoreRuntime();
            case RENESAS_UNO -> new RenesasCoreRuntime();
            case ZEPHYR -> new ZephyrCoreRuntime();
            case MEGAAVR -> new MegaAvrCoreRuntime();
            case ESP32 -> new Esp32CoreRuntime(ArduinoCore.ESP32);
            case ESP32_CAM -> new Esp32CoreRuntime(ArduinoCore.ESP32_CAM);
            case M5STACK_ESP32 -> new Esp32CoreRuntime(ArduinoCore.M5STACK_ESP32);
            case RP2350 -> new Rp2350CoreRuntime();
        };
    }
}
