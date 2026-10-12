package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.board.ArduinoCore;

/** The one place that names a {@link CoreRuntime} implementation: each {@link ArduinoCore} maps to exactly one. */
public final class CoreRuntimes {

    public CoreRuntime of(ArduinoCore core) {
        return switch (core) {
            case RENESAS_UNO -> new RenesasCoreRuntime();
            case ZEPHYR -> new ZephyrCoreRuntime();
            case MEGAAVR -> new MegaAvrCoreRuntime();
        };
    }
}
