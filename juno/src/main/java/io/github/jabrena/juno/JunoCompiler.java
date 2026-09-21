package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.CortexM4AsmBackend;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Program;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JunoCompiler {
    private final CompilationPipeline pipeline = new CompilationPipeline();

    public CompilationResult compile(CompilationRequest request) {
        Program program = pipeline.link(request.classPath(), request.mainClass());
        IrProgram optimized = pipeline.optimize(pipeline.lower(program));
        CortexM4AsmBackend.Output output = new CortexM4AsmBackend(request.gcLoggingEnabled()).generate(optimized);
        return new CompilationResult(output.assembly(), output.runtimeShim(), output.entryPointSymbol(),
                CompilationReport.from(program, optimized));
    }

    public CompilationResult compile(List<Path> classPath, String mainClass) {
        return compile(new CompilationRequest(classPath, mainClass));
    }

    public CompilationResult compile(List<Path> classPath, String mainClass, boolean gcLoggingEnabled) {
        return compile(new CompilationRequest(classPath, mainClass, gcLoggingEnabled));
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput) {
        return compileTo(classPath, mainClass, assemblyOutput, runtimeShimOutput, false);
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput, boolean gcLoggingEnabled) {
        CompilationResult result = compile(classPath, mainClass, gcLoggingEnabled);
        write(assemblyOutput, result.assembly(), "assembly");
        write(runtimeShimOutput, result.runtimeShim(), "runtime shim");
        return result;
    }

    private static void write(Path output, String source, String description) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CompileException("Cannot write generated " + description + " to " + output, exception);
        }
    }
}
