package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.ConfigAnalyzer;
import io.github.jabrena.juno.analysis.ConfigSuggestion;
import io.github.jabrena.juno.analysis.RuntimeRiskAnalyzer;
import io.github.jabrena.juno.analysis.RuntimeRiskReport;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Program;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What the compiler found while producing a {@link CompilationResult}. */
public record CompilationReport(MethodRef entryPoint, Board board, int reachableMethods, int irBlocks,
                                Set<Intrinsic> intrinsics, RuntimeRiskReport runtimeRisks,
                                List<ConfigSuggestion> configSuggestions) {
    static CompilationReport from(Program program, IrProgram lowered, IrProgram optimized) {
        return from(program, lowered, optimized, RuntimeConfig.DEFAULT);
    }

    /** {@code lowered} is the IR before optimization, where every array access still shows its bounds check. */
    static CompilationReport from(Program program, IrProgram lowered, IrProgram optimized,
                                  RuntimeConfig runtimeConfig) {
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
        RuntimeRiskReport runtimeRisks = new RuntimeRiskAnalyzer(runtimeConfig).analyze(program, optimized, lowered);
        return new CompilationReport(program.entryPoint(), program.board(), program.methods().size(), irBlocks,
                Set.copyOf(intrinsics), runtimeRisks,
                new ConfigAnalyzer(runtimeConfig).analyze(optimized, runtimeRisks, program.board()));
    }
}
