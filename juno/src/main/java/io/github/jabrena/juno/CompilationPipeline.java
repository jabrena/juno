package io.github.jabrena.juno;

import io.github.jabrena.juno.classfile.ClassPath;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Linker;
import io.github.jabrena.juno.linker.Program;
import io.github.jabrena.juno.lowering.BytecodeToIr;
import io.github.jabrena.juno.optimize.BlockMerging;
import io.github.jabrena.juno.optimize.BoundsCheckElimination;
import io.github.jabrena.juno.optimize.CompilerPass;
import io.github.jabrena.juno.optimize.ConstantFolder;
import io.github.jabrena.juno.optimize.CopyPropagation;
import io.github.jabrena.juno.optimize.DeadBlockElimination;
import io.github.jabrena.juno.optimize.DeadLocalStoreElimination;
import io.github.jabrena.juno.optimize.DeadValueElimination;
import io.github.jabrena.juno.optimize.Inliner;
import io.github.jabrena.juno.optimize.ScalarReplacement;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The compiler's stages, named and callable independently: classfiles -&gt; {@link #link} -&gt;
 * {@link #lower} -&gt; {@link #optimize}. {@link JunoCompiler} is the stable public
 * entry point that runs them in sequence; this class exists so later work (a compilation report, {@code juno
 * inspect}) can hook into any one stage's output without re-deriving it or re-threading the whole pipeline.
 */
final class CompilationPipeline {
    private static final List<CompilerPass> OPTIMIZATION_PASSES = List.of(
            new Inliner(), new BlockMerging(), new CopyPropagation(), new ConstantFolder(),
            new DeadBlockElimination(), new BlockMerging(),
            // With small constructors and accessors inlined, an object that never leaves the method can live in
            // local slots; a second round then sees through those slots. Dead stores go first: javac parks a new
            // object in a local whose loads copy propagation already removed, and that store is not a real escape.
            new DeadLocalStoreElimination(), new ScalarReplacement(), new CopyPropagation(), new ConstantFolder(),
            new DeadBlockElimination(), new BlockMerging(), new BoundsCheckElimination(),
            new DeadValueElimination(), new DeadLocalStoreElimination());

    Program link(List<Path> classPath, String mainClass) {
        return link(classPath, mainClass, Optional.empty());
    }

    Program link(List<Path> classPath, String mainClass, Optional<String> requestedBoard) {
        Map<String, JavaClass> classes = new ClassPath().load(classPath);
        return new Linker().link(classes, mainClass, requestedBoard);
    }

    IrProgram lower(Program program) {
        return new BytecodeToIr().lower(program);
    }

    IrProgram optimize(IrProgram program) {
        IrProgram optimized = program;
        for (CompilerPass pass : OPTIMIZATION_PASSES) {
            optimized = pass.apply(optimized);
        }
        return optimized;
    }
}
