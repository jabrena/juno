/**
 * Wi-Fi connectivity for the UNO R4 WiFi, UNO Q and UNO WiFi Rev2:
 * {@link io.github.jabrena.juno.hardware.net.Wifi} connects and reads the board's address. Built on top of this
 * connection, the {@link io.github.jabrena.juno.hardware.net.udp} subpackage provides allocation-free UDP and the SNTP
 * clock behind {@link java.time.LocalDateTime#now()}, {@link io.github.jabrena.juno.hardware.net.http} HTTP(S) client/server support and JSON reading, and
 * {@link io.github.jabrena.juno.hardware.net.email} basic SMTP send / POP3S read.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net;

import org.jspecify.annotations.NullMarked;
