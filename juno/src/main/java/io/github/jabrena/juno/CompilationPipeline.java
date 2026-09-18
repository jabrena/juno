package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.ArduinoCppBackend;
import io.github.jabrena.juno.classfile.ClassPath;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Linker;
import io.github.jabrena.juno.linker.Program;
import io.github.jabrena.juno.lowering.BytecodeToIr;
import io.github.jabrena.juno.optimize.CompilerPass;
import io.github.jabrena.juno.optimize.ConstantFolder;
import io.github.jabrena.juno.optimize.DeadBlockElimination;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The compiler's stages, named and callable independently: classfiles -&gt; {@link #link} -&gt;
 * {@link #lower} -&gt; {@link #optimize} -&gt; {@link #generate}. {@link JunoCompiler} is the stable public
 * entry point that runs them in sequence; this class exists so later work (a compilation report, {@code juno
 * inspect}) can hook into any one stage's output without re-deriving it or re-threading the whole pipeline.
 */
final class CompilationPipeline {
    private static final List<CompilerPass> OPTIMIZATION_PASSES = List.of(
            new ConstantFolder(), new DeadBlockElimination());

    Program link(List<Path> classPath, String mainClass) {
        Map<String, JavaClass> classes = new ClassPath().load(classPath);
        return new Linker().link(classes, mainClass);
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

    String generate(IrProgram program) {
        return new ArduinoCppBackend().generate(program);
    }
}
