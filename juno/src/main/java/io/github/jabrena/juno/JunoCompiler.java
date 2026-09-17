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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class JunoCompiler {
    private static final List<CompilerPass> OPTIMIZATION_PASSES = List.of(
            new ConstantFolder(), new DeadBlockElimination());

    public String compile(List<Path> classPath, String mainClass) {
        Map<String, JavaClass> classes = new ClassPath().load(classPath);
        Program program = new Linker().link(classes, mainClass);
        IrProgram ir = new BytecodeToIr().lower(program);
        for (CompilerPass pass : OPTIMIZATION_PASSES) {
            ir = pass.apply(ir);
        }
        return new ArduinoCppBackend().generate(ir);
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
