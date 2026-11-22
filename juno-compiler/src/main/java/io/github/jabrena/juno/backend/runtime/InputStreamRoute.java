package io.github.jabrena.juno.backend.runtime;

/**
 * Where a program's {@code java.io.InputStream} calls go. Juno lowers every {@code InputStream} method to one shared
 * runtime function, so a program has a single route, chosen from the streams it can open.
 */
public enum InputStreamRoute {
    /** SD card files, the default; the SD runtime also hands a socket's reads on to it. */
    SD,
    /** Only a socket's input stream: reads go straight to the socket and the SD runtime is left out. */
    SOCKET,
    /**
     * A board-side {@code InputStream}, such as an HTTP server's request body: reads go through the registered-stream
     * table in {@code common/streams.cpp}, which passes any other handle on to the socket or SD runtime.
     */
    STREAMS
}
