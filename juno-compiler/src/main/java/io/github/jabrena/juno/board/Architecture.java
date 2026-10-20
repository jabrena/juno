package io.github.jabrena.juno.board;

import io.github.jabrena.juno.CompileException;

/**
 * The instruction set an {@link ArduinoCore} runs generated code on, which picks the code generator: the UNO R4's
 * Cortex-M4 and the UNO Q's Cortex-M33 share one Thumb-2 backend, the UNO WiFi Rev2's ATmega4809 has its own AVR one,
 * and the Nano ESP32's ESP32-S3 an Xtensa one.
 */
public enum Architecture {
    /** ARMv7-M/ARMv8-M Thumb-2: 32-bit registers and pointers. */
    THUMB2("experimental Thumb-2 assembly backend"),
    /** 8-bit AVR (avrxmega3): 16-bit pointers, and a toolchain whose {@code int} and {@code double} are narrower than Java's. */
    AVR("AVR assembly backend (UNO WiFi Rev2)"),
    /** Xtensa LX7 with the windowed-register ABI: 32-bit registers and pointers. */
    XTENSA("Xtensa assembly backend (Nano ESP32)");

    private final String backendName;

    Architecture(String backendName) {
        this.backendName = backendName;
    }

    /**
     * The error for a program this architecture's backend or runtime cannot generate yet, so the code generator and the
     * runtime shim report it the same way without depending on each other.
     */
    public CompileException unsupported(String detail) {
        return new CompileException("The " + backendName + " does not support this yet: " + detail);
    }
}
