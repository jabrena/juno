package io.github.jabrena.juno;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic structural measurements of one Juno compilation.
 *
 * <p>These measurements are deliberately independent of host timing: they can be compared in tests and CI
 * without mistaking a faster build machine for a compiler optimization. Instruction counts describe Juno's
 * generated assembly source; final flash/RAM sizes still come from the Arduino toolchain's ELF, and execution
 * cycles still need the target board.
 *
 * @param assemblyBytes UTF-8 bytes in the generated assembly source
 * @param runtimeShimBytes UTF-8 bytes in the generated C++ runtime shim
 * @param generatedMethods generated assembly functions
 * @param assemblyInstructions non-directive assembly instructions
 * @param loadInstructions ARM load instructions ({@code ldr*})
 * @param storeInstructions ARM store instructions ({@code str*})
 * @param branchInstructions conditional and unconditional branches, excluding calls
 * @param callInstructions branch-with-link calls
 * @param directAllocationCalls direct calls to {@code juno_alloc}
 * @param maximumFixedFrameBytes largest generated method frame, including saved registers
 * @param totalFixedFrameBytes sum of generated method frames, including saved registers
 */
public record CompilationMetrics(int assemblyBytes, int runtimeShimBytes, int generatedMethods,
                                 int assemblyInstructions, int loadInstructions, int storeInstructions,
                                 int branchInstructions, int callInstructions, int directAllocationCalls,
                                 int maximumFixedFrameBytes, int totalFixedFrameBytes) {
    private static final int SAVED_REGISTER_BYTES = 9 * Integer.BYTES;
    private static final Set<String> BRANCH_OPCODES = Set.of(
            "b", "beq", "bne", "bhs", "blo", "bmi", "bpl", "bvs", "bvc", "bhi", "bls",
            "bge", "blt", "bgt", "ble", "bx", "cbz", "cbnz");

    /** Measures the generated sources in {@code result}. */
    public static CompilationMetrics from(CompilationResult result) {
        Objects.requireNonNull(result, "result");
        return from(result.assembly(), result.runtimeShim());
    }

    /** Measures generated assembly and runtime-shim sources without executing either one. */
    public static CompilationMetrics from(String assembly, String runtimeShim) {
        Objects.requireNonNull(assembly, "assembly");
        Objects.requireNonNull(runtimeShim, "runtimeShim");

        Counters counters = new Counters();
        for (String sourceLine : assembly.lines().toList()) {
            counters.accept(sourceLine.trim());
        }
        counters.finishFrame();
        return new CompilationMetrics(assembly.getBytes(StandardCharsets.UTF_8).length,
                runtimeShim.getBytes(StandardCharsets.UTF_8).length, counters.generatedMethods,
                counters.instructions, counters.loads, counters.stores, counters.branches, counters.calls,
                counters.directAllocations, counters.maximumFrameBytes, counters.totalFrameBytes);
    }

    private static final class Counters {
        private int generatedMethods;
        private int instructions;
        private int loads;
        private int stores;
        private int branches;
        private int calls;
        private int directAllocations;
        private int maximumFrameBytes;
        private int totalFrameBytes;
        private int currentFrameBytes = -1;
        private boolean readingPrologue;
        private boolean r12Known;
        private int r12Value;

        private void accept(String line) {
            if (line.isEmpty() || line.startsWith("@") || line.startsWith(".") || line.endsWith(":")) {
                return;
            }
            String opcode = line.split("\\s+", 2)[0];
            instructions++;
            if (opcode.startsWith("ldr")) loads++;
            if (opcode.startsWith("str")) stores++;
            if (BRANCH_OPCODES.contains(opcode)) branches++;
            if (opcode.equals("bl") || opcode.equals("blx")) {
                calls++;
                if (line.equals("bl juno_alloc")) directAllocations++;
            }

            if (line.equals("push {r4-r11, lr}")) {
                finishFrame();
                generatedMethods++;
                currentFrameBytes = SAVED_REGISTER_BYTES;
                readingPrologue = true;
                r12Known = false;
                r12Value = 0;
                return;
            }
            if (!readingPrologue) return;
            if (readR12Immediate(line)) return;
            if (line.equals("sub sp, sp, r12") && r12Known) {
                currentFrameBytes += r12Value;
                readingPrologue = false;
                return;
            }
            readingPrologue = false;
        }

        private boolean readR12Immediate(String line) {
            if (line.startsWith("movs r12, #")) {
                r12Value = parseImmediate(line);
                r12Known = true;
                return true;
            }
            if (line.startsWith("movw r12, #")) {
                r12Value = parseImmediate(line) & 0xFFFF;
                r12Known = true;
                return true;
            }
            if (line.startsWith("movt r12, #") && r12Known) {
                r12Value |= (parseImmediate(line) & 0xFFFF) << 16;
                return true;
            }
            return false;
        }

        private int parseImmediate(String line) {
            return Integer.parseInt(line.substring(line.indexOf('#') + 1));
        }

        private void finishFrame() {
            if (currentFrameBytes < 0) return;
            totalFrameBytes += currentFrameBytes;
            maximumFrameBytes = Math.max(maximumFrameBytes, currentFrameBytes);
            currentFrameBytes = -1;
            readingPrologue = false;
        }
    }
}
