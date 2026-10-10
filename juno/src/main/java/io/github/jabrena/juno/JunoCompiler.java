package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.Thumb2AsmBackend;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Program;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class JunoCompiler {
    private final CompilationPipeline pipeline = new CompilationPipeline();

    public CompilationResult compile(CompilationRequest request) {
        Program program = pipeline.link(request.classPath(), request.mainClass(), request.requestedBoard());
        IrProgram optimized = pipeline.optimize(pipeline.lower(program));
        Thumb2AsmBackend.Output output = new Thumb2AsmBackend(request.gcLoggingEnabled(), program.board(), request.runtimeConfig())
                .generate(optimized);
        return new CompilationResult(output.assembly(), output.runtimeShim(), output.entryPointSymbol(),
                CompilationReport.from(program, optimized, request.runtimeConfig()));
    }

    public CompilationResult compile(List<Path> classPath, String mainClass) {
        return compile(new CompilationRequest(classPath, mainClass));
    }

    public CompilationResult compile(List<Path> classPath, String mainClass, boolean gcLoggingEnabled) {
        return compile(new CompilationRequest(classPath, mainClass, gcLoggingEnabled));
    }

    public CompilationResult compile(List<Path> classPath, String mainClass, boolean gcLoggingEnabled,
                                     Optional<String> requestedBoard) {
        return compile(new CompilationRequest(classPath, mainClass, gcLoggingEnabled, requestedBoard));
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput) {
        return compileTo(classPath, mainClass, assemblyOutput, runtimeShimOutput, false);
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput, boolean gcLoggingEnabled) {
        return compileTo(classPath, mainClass, assemblyOutput, runtimeShimOutput, gcLoggingEnabled, Optional.empty());
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput, boolean gcLoggingEnabled,
                                       Optional<String> requestedBoard) {
        CompilationResult result = compile(classPath, mainClass, gcLoggingEnabled, requestedBoard);
        write(assemblyOutput, result.assembly(), "assembly");
        write(runtimeShimOutput, result.runtimeShim(), "runtime shim");
        return result;
    }

    /** Compiles {@code request} and writes the generated assembly and runtime shim. */
    public CompilationResult compileTo(CompilationRequest request, Path assemblyOutput, Path runtimeShimOutput) {
        CompilationResult result = compile(request);
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
