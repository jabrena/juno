package io.github.jabrena.juno.backend.runtime;

/**
 * The arena and its conservative mark/sweep collector, {@code juno_alloc}, shared by every architecture: it scans
 * {@code uintptr_t}-wide words, so it finds 16-bit AVR references as well as 32-bit ARM ones.
 */
final class AllocatorRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    /**
     * @param arrayLengthBytes width of the length word in front of an array's elements, which the collector must
     *     step over to find the block an array reference points into
     * @param gcStackScan the statement scanning the live stack(s) from {@code stackMarker}
     * @param gcThreadDeclaration declarations that statement needs, or {@code ""}
     */
    String source(int arenaBytes, int arrayLengthBytes, boolean gcLoggingEnabled, String gcStackScan,
                  String gcThreadDeclaration) {
        return source(arenaBytes, arrayLengthBytes, gcLoggingEnabled, gcStackScan, gcThreadDeclaration, 1);
    }

    /**
     * The allocator for {@code carrierCount} carriers. With several, allocation takes the runtime lock and a
     * collection stops every other carrier first (see {@code common/carrier-gc.cpp}); one carrier keeps the original
     * text byte for byte.
     */
    String source(int arenaBytes, int arrayLengthBytes, boolean gcLoggingEnabled, String gcStackScan,
                  String gcThreadDeclaration, int carrierCount) {
        String source = singleCarrierSource(arenaBytes, arrayLengthBytes, gcLoggingEnabled, gcStackScan,
                gcThreadDeclaration);
        return carrierCount == 1 ? source : source
                .replace("static void juno_gc_collect() {\n", """
                        static void juno_gc_stop_world();
                        static void juno_gc_resume_world();
                        extern "C" void juno_runtime_enter();
                        extern "C" void juno_runtime_exit();

                        static void juno_gc_collect() {
                          juno_gc_stop_world();
                        """)
                .replace("  juno_gc_sweep();\n", "  juno_gc_sweep();\n  juno_gc_resume_world();\n")
                .replace("extern \"C\" void* juno_alloc(uint32_t size, uint32_t alignment) {", """
                        static void* juno_alloc_unlocked(uint32_t size, uint32_t alignment);

                        // Both carriers allocate from one arena.
                        extern "C" void* juno_alloc(uint32_t size, uint32_t alignment) {
                          juno_runtime_enter();
                          void* memory = juno_alloc_unlocked(size, alignment);
                          juno_runtime_exit();
                          return memory;
                        }

                        static void* juno_alloc_unlocked(uint32_t size, uint32_t alignment) {""");
    }

    private String singleCarrierSource(int arenaBytes, int arrayLengthBytes, boolean gcLoggingEnabled,
                                       String gcStackScan, String gcThreadDeclaration) {
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
                .replace("${JUNO_ARRAY_LENGTH_BYTES}", Integer.toString(arrayLengthBytes))
                .replace("${JUNO_GC_STACK_SCAN}", gcStackScan)
                .replace("${JUNO_GC_THREAD_DECLARATION}", gcThreadDeclaration)
                .replace("${JUNO_GC_LOG_BEFORE}", gcLogBefore)
                .replace("${JUNO_GC_LOG_AFTER}", gcLogAfter);
    }
}
