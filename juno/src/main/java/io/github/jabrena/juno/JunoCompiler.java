package io.github.jabrena.juno;

import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.linker.Program;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JunoCompiler {
    private final CompilationPipeline pipeline = new CompilationPipeline();

    public String compile(List<Path> classPath, String mainClass) {
        return compile(new CompilationRequest(classPath, mainClass)).generatedSource();
    }

    public CompilationResult compile(CompilationRequest request) {
        Program program = pipeline.link(request.classPath(), request.mainClass());
        IrProgram ir = pipeline.lower(program);
        IrProgram optimized = pipeline.optimize(ir);
        String source = pipeline.generate(optimized);
        return new CompilationResult(source, CompilationReport.from(program, optimized));
    }

    public void compileTo(List<Path> classPath, String mainClass, Path output) {
        String source = compile(classPath, mainClass);
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CompileException("Cannot write generated sketch to " + output, exception);
        }
    }
}
