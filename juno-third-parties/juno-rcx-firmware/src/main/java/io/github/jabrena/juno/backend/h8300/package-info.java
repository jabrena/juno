/**
 * The Hitachi H8/300 target of the LEGO RCX brick: {@link io.github.jabrena.juno.backend.h8300.H8Assembler} encodes
 * H8/300 machine code in Java, with no external toolchain, into an {@link io.github.jabrena.juno.backend.h8300.H8Image}
 * written as Motorola S-records, the format the RCX firmware downloaders take.
 * {@link io.github.jabrena.juno.backend.h8300.RcxRemoteFirmware} answers the LEGO infrared direct commands for motors,
 * sensors, sounds, the display and the battery, and the RCX Remote Control's buttons.
 */
@NullMarked
package io.github.jabrena.juno.backend.h8300;

import org.jspecify.annotations.NullMarked;
