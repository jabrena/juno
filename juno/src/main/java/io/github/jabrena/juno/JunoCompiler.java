package io.github.jabrena.juno;

import io.github.jabrena.juno.backend.ArduinoCppBackend;
import io.github.jabrena.juno.classfile.ClassPath;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.linker.Linker;
import io.github.jabrena.juno.linker.Program;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class JunoCompiler {
    public String compile(List<Path> classPath, String mainClass) {
        Map<String, JavaClass> classes = new ClassPath().load(classPath);
        Program program = new Linker().link(classes, mainClass);
        return new ArduinoCppBackend().generate(program);
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
