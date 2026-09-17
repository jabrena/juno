package io.github.jabrena.juno;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CompilerTestSupport {
    private CompilerTestSupport() {
    }

    static Path compileJava(Path directory, String className, String source) throws IOException {
        Path sourceFile = directory.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                "--release", "17",
                "-classpath", System.getProperty("java.class.path"),
                "-d", directory.toString(),
                sourceFile.toString());
        if (result != 0) {
            throw new AssertionError("Fixture javac failed with exit code " + result);
        }
        return directory;
    }

    static String compileJuno(Path classes, String mainClass) {
        return new JunoCompiler().compile(List.of(classes), mainClass);
    }
}
