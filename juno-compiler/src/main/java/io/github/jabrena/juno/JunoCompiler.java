package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.AsmBackend;
import io.github.jabrena.juno.backend.GeneratedCode;
import io.github.jabrena.juno.backend.avr.AvrAsmBackend;
import io.github.jabrena.juno.backend.thumb2.Thumb2AsmBackend;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Program;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Compiles a Java program to assembly plus its runtime shim. Every overload builds a {@link CompilationRequest}
 * and delegates to {@link #compile(CompilationRequest)}.
 */
public final class JunoCompiler {
    private final CompilationPipeline pipeline = new CompilationPipeline();

    public CompilationResult compile(CompilationRequest request) {
        Program program = pipeline.link(request.classPath(), request.mainClass(), request.requestedBoard());
        IrProgram lowered = pipeline.lower(program);
        IrProgram optimized = pipeline.optimize(lowered);
        RuntimeConfig runtimeConfig = request.runtimeConfig().forBoard(program.board());
        CompilationReport report = CompilationReport.from(program, lowered, optimized, runtimeConfig);
        GeneratedCode code = backendFor(program.board(), request.gcLoggingEnabled(), runtimeConfig)
                .generate(optimized);
        return new CompilationResult(code.assembly(), code.runtimeShim(), code.entryPointSymbol(), report);
    }

    /** Compiles {@code request} and writes the generated assembly and runtime shim. */
    public CompilationResult compileTo(CompilationRequest request, Path assemblyOutput, Path runtimeShimOutput) {
        CompilationResult result = compile(request);
        result.writeTo(assemblyOutput, runtimeShimOutput);
        return result;
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
        return compileTo(new CompilationRequest(classPath, mainClass), assemblyOutput, runtimeShimOutput);
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput, boolean gcLoggingEnabled) {
        return compileTo(new CompilationRequest(classPath, mainClass, gcLoggingEnabled), assemblyOutput,
                runtimeShimOutput);
    }

    public CompilationResult compileTo(List<Path> classPath, String mainClass, Path assemblyOutput,
                                       Path runtimeShimOutput, boolean gcLoggingEnabled,
                                       Optional<String> requestedBoard) {
        return compileTo(new CompilationRequest(classPath, mainClass, gcLoggingEnabled, requestedBoard),
                assemblyOutput, runtimeShimOutput);
    }

    /** A fresh backend for {@code board}'s instruction-set architecture. */
    private AsmBackend backendFor(Board board, boolean gcLoggingEnabled, RuntimeConfig runtimeConfig) {
        return switch (board.core().architecture()) {
            case THUMB2 -> new Thumb2AsmBackend(gcLoggingEnabled, board, runtimeConfig);
            case AVR -> new AvrAsmBackend(gcLoggingEnabled, board, runtimeConfig);
        };
    }
}
