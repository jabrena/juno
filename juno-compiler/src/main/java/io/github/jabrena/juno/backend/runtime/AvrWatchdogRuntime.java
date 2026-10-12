package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;

import java.util.Optional;

/** Emits the AVR shim's optional watchdog functions and panic diagnostic. */
final class AvrWatchdogRuntime {
    private final Optional<WatchdogRuntime> watchdog;

    AvrWatchdogRuntime(CoreRuntime core, Optional<Integer> timeoutMillis) {
        this.watchdog = timeoutMillis.map(core::watchdog);
    }

    String includes() {
        return watchdog.map(WatchdogRuntime::includes).orElse("");
    }

    String functions() {
        return watchdog.map(config -> """

                extern "C" void juno_watchdog_refresh() {
                  ${JUNO_WATCHDOG_REFRESH}
                }

                extern "C" void juno_watchdog_begin() {
                  ${JUNO_WATCHDOG_BEGIN}
                }

                extern "C" void yield() {
                  juno_watchdog_refresh();
                }
                """
                .replace("${JUNO_WATCHDOG_REFRESH}", config.refresh())
                .replace("${JUNO_WATCHDOG_BEGIN}", config.begin())).orElse("");
    }

    String panicMessage() {
        return watchdog.map(config ->
                "  Serial.println(\"[juno-watchdog] panic: board will reset via watchdog in approximately "
                        + config.effectiveTimeoutMillis() + "ms\");")
                .orElse("");
    }
}
