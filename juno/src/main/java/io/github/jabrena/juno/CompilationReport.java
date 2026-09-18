package io.github.jabrena.juno;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Program;

import java.util.LinkedHashSet;
import java.util.Set;

/** What the compiler found while producing a {@link CompilationResult}: reachability and intrinsic usage. */
public record CompilationReport(MethodRef entryPoint, int reachableMethods, int irBlocks, Set<Intrinsic> intrinsics) {
    static CompilationReport from(Program program, IrProgram optimized) {
        int irBlocks = optimized.methods().stream().mapToInt(method -> method.blocks().size()).sum();
        Set<Intrinsic> intrinsics = new LinkedHashSet<>();
        for (IrMethod method : optimized.methods()) {
            for (IrBasicBlock block : method.blocks()) {
                for (IrInstruction instruction : block.instructions()) {
                    if (instruction instanceof IrInstruction.IntrinsicCall call) {
                        intrinsics.add(call.intrinsic());
                    }
                }
            }
        }
        return new CompilationReport(program.entryPoint(), program.methods().size(), irBlocks, Set.copyOf(intrinsics));
    }
}
