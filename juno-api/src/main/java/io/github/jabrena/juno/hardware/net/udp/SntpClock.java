package io.github.jabrena.juno.hardware.net.udp;

import java.util.Properties;

/**
 * The SNTP server behind the internet-backed {@link java.time.LocalDateTime#now()} intrinsic, which synchronizes UTC
 * over one UDP exchange on its own socket (separate from {@link Udp}'s) and then advances it with the board's
 * millisecond counter. The build-time {@code JUNO_SNTP_HOST} (default {@code pool.ntp.org}) and {@code JUNO_SNTP_PORT}
 * (default {@code 123}) pick the server; {@link #configure} can replace them at run time.
 */
public final class SntpClock {
    private SntpClock() {
    }

    /**
     * Applies optional {@code sntp.host} and {@code sntp.port} values loaded by the application, normally from
     * {@code application.properties} on an SD card. Call this once during startup, before the first {@code
     * LocalDateTime.now()}. A missing property keeps the build-time value. Returns {@code false} when neither property
     * exists or either supplied value is invalid (a host of 64 or more characters, or other than letters, digits, dots
     * and hyphens; a port outside 1..65535); in that case the configuration is unchanged.
     */
    public static native boolean configure(Properties properties);
}
