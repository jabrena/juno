package io.github.jabrena.juno.backend.runtime;

/**
 * The arena and its conservative mark/sweep collector, {@code juno_alloc}, shared by every architecture: it scans
 * {@code uintptr_t}-wide words, so it finds 16-bit AVR references as well as 32-bit ARM ones.
 */
final class AllocatorRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    /**
     * @param gcStackScan the statement scanning the live stack(s) from {@code stackMarker}
     * @param gcThreadDeclaration declarations that statement needs, or {@code ""}
     */
    String source(int arenaBytes, boolean gcLoggingEnabled, String gcStackScan, String gcThreadDeclaration) {
        // Emitted (or not) once here, at Java-string-generation time — like every other optional
        // shim feature (ShimFeature.MOUSE, ShimFeature.WIFI, ...) — rather than a C++-level runtime branch, so a
        // program compiled without --gc-log/juno.gcLog pays zero extra flash/RAM/Serial time for it.
        String gcLogBefore = gcLoggingEnabled ? "uint32_t junoGcLogUsedBefore = juno_arena_used;" : "";
        String gcLogAfter = gcLoggingEnabled
                ? """
                Serial.print("[juno-gc] collect: used "); Serial.print(junoGcLogUsedBefore); \
                Serial.print(" -> "); Serial.println(juno_arena_used);"""
                : "";
        return runtimeSource.load("common/allocator.cpp")
                .replace("${JUNO_ARENA_CAPACITY}", Integer.toString(arenaBytes))
                .replace("${JUNO_GC_STACK_SCAN}", gcStackScan)
                .replace("${JUNO_GC_THREAD_DECLARATION}", gcThreadDeclaration)
                .replace("${JUNO_GC_LOG_BEFORE}", gcLogBefore)
                .replace("${JUNO_GC_LOG_AFTER}", gcLogAfter);
    }
}
