package io.github.jabrena.juno.board;

/**
 * What decides whether an arena below a board's maximum actually fits. A link-time check only proves the sizes fit
 * the linker's RAM region, so the authority says where a too-large arena fails.
 */
public enum MemoryAuthority {
    /** The linker places every section, so a successful link is the final word (AVR, UNO R4 WiFi). */
    LINKER,
    /** Task stacks, Wi-Fi, TLS and BLE take memory from the run-time heap, which fails only at run time. */
    RUNTIME_HEAP,
    /** The sketch's code and the arena share one loader pool filled at boot (UNO Q). */
    LOADER_POOL
}
