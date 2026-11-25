package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.AsmBackend;
import io.github.jabrena.juno.backend.GeneratedCode;
import io.github.jabrena.juno.backend.avr.AvrAsmBackend;
import io.github.jabrena.juno.backend.runtime.LinkedDecisionModel;
import io.github.jabrena.juno.backend.runtime.LinkedLanguageModel;
import io.github.jabrena.juno.backend.thumb2.Thumb2AsmBackend;
import io.github.jabrena.juno.backend.xtensa.XtensaAsmBackend;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.intrinsic.Intrinsic;
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
        ResolvedRuntimeConfig runtimeConfig = request.runtimeConfig().resolveFor(program.board());
        CompilationReport report = CompilationReport.from(program, lowered, optimized, runtimeConfig);
        GeneratedCode code = backendFor(program.board(), request.gcLoggingEnabled(), runtimeConfig)
                .generate(optimized);
        String runtimeShim = code.runtimeShim() + linkedDecisionModel(request, report)
                + linkedLanguageModel(request, report);
        return new CompilationResult(code.assembly(), runtimeShim, code.entryPointSymbol(), report,
                program.board().fqbn() + code.fqbnOptions(), code.mainLauncher(), code.coprocessor());
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

    /**
     * The JEV1 model {@code DecisionModel.begin()} uses, appended after the evaluator it calls, or nothing for a program
     * that never calls it.
     */
    private String linkedDecisionModel(CompilationRequest request, CompilationReport report) {
        if (!report.intrinsics().contains(Intrinsic.DECISION_MODEL_BEGIN_LINKED)) {
            return "";
        }
        Path model = request.decisionModel().orElseThrow(() -> new CompileException(
                "DecisionModel.begin() links the JEV1 model named by -Djuno.dm.model=<file>, and none was given; "
                        + "pass it, or load the model from an SD card with DecisionModel.begin(SdCard, String)"));
        return LinkedDecisionModel.read(model).source();
    }

    /**
     * The JLM1 model {@code LanguageModel.begin()} loads, appended after the PSRAM helpers it uses, or nothing for a
     * program that never calls it.
     */
    private String linkedLanguageModel(CompilationRequest request, CompilationReport report) {
        if (!report.intrinsics().contains(Intrinsic.LANGUAGE_MODEL_BUNDLED)) {
            return "";
        }
        Path model = request.languageModel().orElseThrow(() -> new CompileException(
                "LanguageModel.begin() links the JLM1 model named by -Djuno.lm.model=<file>, and none was given; "
                        + "pass it (juno-lm's tracked model is juno-ai/juno-lm/src/main/resources/lm/JUNO.BIN), or load the "
                        + "model from an SD card with LanguageModel.begin(SdCard, String)"));
        return LinkedLanguageModel.read(model).source();
    }

    /** A fresh backend for {@code board}'s instruction-set architecture. */
    private AsmBackend backendFor(Board board, boolean gcLoggingEnabled, ResolvedRuntimeConfig runtimeConfig) {
        return switch (board.core().architecture()) {
            case THUMB2 -> new Thumb2AsmBackend(gcLoggingEnabled, board, runtimeConfig);
            case AVR -> new AvrAsmBackend(gcLoggingEnabled, board, runtimeConfig);
            case XTENSA -> new XtensaAsmBackend(gcLoggingEnabled, board, runtimeConfig);
        };
    }
}
