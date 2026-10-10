/**
 * WiFi connectivity for the Arduino UNO R4 WiFi and UNO Q:
 * {@link io.github.jabrena.juno.api.net.Wifi} connects and reads the board's address, while
 * {@link io.github.jabrena.juno.api.net.Udp} provides allocation-free discovery, send, and
 * receive primitives. See the
 * {@link io.github.jabrena.juno.api.net.http} subpackage for HTTP(S) client/server support and
 * JSON reading, and {@link io.github.jabrena.juno.api.net.email} for basic SMTP send / POP3S
 * read, both built on top of this connection.
 */
@NullMarked
package io.github.jabrena.juno.api.net;

import org.jspecify.annotations.NullMarked;
