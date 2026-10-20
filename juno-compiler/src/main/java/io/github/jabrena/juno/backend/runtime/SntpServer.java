package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.util.Map;

/**
 * The SNTP server {@code LocalDateTime.now()} synchronizes with, from the build-time {@code JUNO_SNTP_HOST} (default
 * {@code pool.ntp.org}) and {@code JUNO_SNTP_PORT} (default 123). {@code SntpClock.configure} can replace both at
 * run time from the SD card's {@code sntp.host}/{@code sntp.port}.
 */
record SntpServer(String host, int port) {
    static final String HOST_VARIABLE = "JUNO_SNTP_HOST";
    static final String PORT_VARIABLE = "JUNO_SNTP_PORT";
    static final String DEFAULT_HOST = "pool.ntp.org";
    static final int DEFAULT_PORT = 123;
    /** Host buffer in the shim, terminator included. */
    static final int HOST_CAPACITY = 64;

    static SntpServer from(Map<String, String> environment) {
        String host = environment.getOrDefault(HOST_VARIABLE, DEFAULT_HOST).trim();
        if (host.isEmpty() || host.length() >= HOST_CAPACITY
                || !host.chars().allMatch(character -> Character.isLetterOrDigit(character)
                        || character == '.' || character == '-')) {
            throw new CompileException(HOST_VARIABLE + " must be a host name or IPv4 address of at most "
                    + (HOST_CAPACITY - 1) + " letters, digits, dots and hyphens: " + host);
        }
        String portText = environment.getOrDefault(PORT_VARIABLE, Integer.toString(DEFAULT_PORT)).trim();
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException exception) {
            throw new CompileException(PORT_VARIABLE + " must be a port number: " + portText, exception);
        }
        if (port < 1 || port > 65535) {
            throw new CompileException(PORT_VARIABLE + " must be between 1 and 65535: " + port);
        }
        return new SntpServer(host, port);
    }

    /** {@link #host()} as a C++ string literal; it holds no quote or backslash, which {@link #from} rejects. */
    String cppHost() {
        return '"' + host + '"';
    }
}
