/**
 * The SNTP clock behind {@link java.time.LocalDateTime#now()}: {@link io.github.jabrena.juno.hardware.net.udp.SntpClock}
 * configures the server, and the exchange runs on a {@link java.net.DatagramSocket} of its own. Programs use the JDK's
 * socket classes directly; their subset is described in {@code io.github.jabrena.juno.intrinsic.DatagramMethods}.
 */
@NullMarked
package io.github.jabrena.juno.hardware.net.udp;

import org.jspecify.annotations.NullMarked;
