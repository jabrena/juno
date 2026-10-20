package io.github.jabrena.juno.api.hardware.net;

import java.util.Properties;

/** Configuration for the internet-backed {@link java.time.LocalDateTime#now()} intrinsic. */
public final class InternetClock {
    private InternetClock() {
    }

    /**
     * Applies optional {@code time.api.url} and {@code time.api.jsonPath} values loaded by the application, normally
     * from {@code application.properties} on an SD card. Call this once during startup, before the first {@code
     * LocalDateTime.now()}. Missing properties retain the build-time environment or built-in defaults. Returns
     * {@code false} when neither property exists or either supplied value is invalid; in that case configuration is
     * unchanged.
     */
    public static native boolean configure(Properties properties);
}
