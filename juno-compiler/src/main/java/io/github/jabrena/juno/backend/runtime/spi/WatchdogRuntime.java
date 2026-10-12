package io.github.jabrena.juno.backend.runtime.spi;

/** Core-specific source fragments for one hardware-watchdog configuration. */
public record WatchdogRuntime(String includes, String begin, String refresh, int effectiveTimeoutMillis) {}
