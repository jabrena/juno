package io.github.jabrena.juno;

import io.github.jabrena.juno.classfile.ClassPath;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.linker.Linker;
import io.github.jabrena.juno.linker.Program;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CompilerTestSupport {
    private CompilerTestSupport() {
    }

    public static Path compileJava(Path directory, String className, String source) throws IOException {
        return compileJava(directory, className, source, "17");
    }

    public static Path compileJava(Path directory, String className, String source, String release)
            throws IOException {
        return compileJava(directory, className, source, release, false);
    }

    public static Path compileJavaWithPreview(Path directory, String className, String source)
            throws IOException {
        return compileJava(directory, className, source, "27", true);
    }

    private static Path compileJava(Path directory, String className, String source, String release,
                                    boolean enablePreview) throws IOException {
        Path sourceFile = directory.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // Include `directory` itself so a class compiled by an earlier call (e.g. a cross-class
        // constant's declaring class) is visible when compiling a later one against it.
        String classPath = System.getProperty("java.class.path") + File.pathSeparator + directory;
        List<String> arguments = new ArrayList<>();
        arguments.add("--release");
        arguments.add(release);
        if (enablePreview) {
            arguments.add("--enable-preview");
        }
        arguments.add("-classpath");
        arguments.add(classPath);
        arguments.add("-d");
        arguments.add(directory.toString());
        arguments.add(sourceFile.toString());
        int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
        if (result != 0) {
            throw new AssertionError("Fixture javac failed with exit code " + result);
        }
        return directory;
    }

    // `target/classes` holds both the compiler's own api/annotations packages and any cross-class
    // fixtures compiled by an earlier compileJava call.
    private static final List<Path> JUNO_CLASSPATH = List.of(Path.of("target/classes"));

    public static CompilationResult compileJuno(Path classes, String mainClass) {
        List<Path> classpath = new ArrayList<>(List.of(classes));
        classpath.addAll(JUNO_CLASSPATH);
        return new JunoCompiler().compile(classpath, mainClass);
    }

    public static Program link(Path classes, String mainClass) {
        return link(classes, mainClass, Optional.empty());
    }

    public static Program link(Path classes, String mainClass, Optional<String> requestedBoard) {
        List<Path> classpath = new ArrayList<>(List.of(classes));
        classpath.addAll(JUNO_CLASSPATH);
        Map<String, JavaClass> loaded = new ClassPath().load(classpath);
        return new Linker().link(loaded, mainClass, requestedBoard);
    }
}
