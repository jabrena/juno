/**
 * UDP over the board's Wi-Fi: {@link io.github.jabrena.juno.hardware.net.udp.Udp} gives a program one allocation-free
 * socket for discovery broadcasts, datagrams to known peers and non-blocking receives, and
 * {@link io.github.jabrena.juno.hardware.net.udp.SntpClock} configures the SNTP server behind
 * {@link java.time.LocalDateTime#now()}, which synchronizes on a socket of its own.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net.udp;

import org.jspecify.annotations.NullMarked;
