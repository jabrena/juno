/**
 * WiFi connectivity for the Arduino UNO R4 WiFi and UNO Q:
 * {@link io.github.jabrena.juno.hardware.net.Wifi} connects and reads the board's address, while
 * {@link io.github.jabrena.juno.hardware.net.Udp} provides allocation-free discovery, send, and
 * receive primitives. See the
 * {@link io.github.jabrena.juno.hardware.net.http} subpackage for HTTP(S) client/server support and
 * JSON reading, and {@link io.github.jabrena.juno.hardware.net.email} for basic SMTP send / POP3S
 * read, all built on top of this connection.
 * {@link io.github.jabrena.juno.hardware.net.InternetClock} configures the endpoint behind the
 * internet-backed {@link java.time.LocalDateTime#now()} subset.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net;

import org.jspecify.annotations.NullMarked;
