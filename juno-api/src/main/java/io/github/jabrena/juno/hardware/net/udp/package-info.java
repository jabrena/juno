/**
 * The SNTP clock behind {@link java.time.LocalDateTime#now()}: {@link io.github.jabrena.juno.hardware.net.udp.SntpClock}
 * configures the server, and the exchange runs on a {@link java.net.DatagramSocket} of its own. Remote logging:
 * {@link io.github.jabrena.juno.hardware.net.udp.Syslog} sends RFC 5424 frames on the program's own socket, received by
 * {@code juno:monitor -Djuno.syslog=<port>}. Programs use the JDK's socket classes directly; their subset is described in
 * {@code io.github.jabrena.juno.intrinsic.DatagramMethods}.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net.udp;

import org.jspecify.annotations.NullMarked;
