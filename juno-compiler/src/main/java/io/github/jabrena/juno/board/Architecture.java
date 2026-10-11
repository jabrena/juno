package io.github.jabrena.juno.board;

/**
 * The instruction set an {@link ArduinoCore} runs generated code on, which picks the code generator: the UNO R4's
 * Cortex-M4 and the UNO Q's Cortex-M33 share one Thumb-2 backend, the UNO WiFi Rev2's ATmega4809 has its own AVR one.
 */
public enum Architecture {
    /** ARMv7-M/ARMv8-M Thumb-2: 32-bit registers and pointers. */
    THUMB2,
    /** 8-bit AVR (avrxmega3): 16-bit pointers, and a toolchain whose {@code int} and {@code double} are narrower than Java's. */
    AVR
}
