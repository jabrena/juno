package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.ClassPath;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@DisplayName("Linker main class and native method validation")
class LinkerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Fails when the main class is not on the classpath")
    void shouldFailWhenTheMainClassIsNotOnTheClasspath() throws Exception {
        // Given
        String source = """
                package demo;
                public final class SomeClass {
                    public static void main(String[] args) {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.SomeClass", source);
        Map<String, io.github.jabrena.juno.classfile.JavaClass> classes =
                new ClassPath().load(CompilerTestSupport.classpath(temporaryDirectory));

        // When & Then
        assertThatThrownBy(() -> new Linker().link(classes, "demo.DoesNotExist", Optional.of(Board.UNO_R4_WIFI.id())))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Main class not found on the classpath: demo.DoesNotExist");
    }

    @Test
    @DisplayName("Fails when the class declares no main method")
    void shouldFailWhenTheClassHasNoMainMethod() throws Exception {
        // Given
        String source = """
                package demo;
                public final class NoMain {
                    static void notMain() {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoMain", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.NoMain"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Main class must declare static void main(String[]) or static void main()");
    }

    @Test
    @DisplayName("Fails when main is not static")
    void shouldFailWhenTheMainMethodIsNotStatic() throws Exception {
        // Given
        String source = """
                package demo;
                public final class InstanceMain {
                    public void main(String[] args) {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.InstanceMain", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.InstanceMain"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Main class must declare static void main(String[]) or static void main()");
    }

    @Test
    @DisplayName("Fails when a reachable native method has no Juno intrinsic")
    void shouldFailWhenAReachableNativeMethodHasNoJunoIntrinsic() throws Exception {
        // Given
        String source = """
                package demo;
                public final class UnmappedNative {
                    private static native void notAJunoIntrinsic();
                    public static void main(String[] args) {
                        notAJunoIntrinsic();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UnmappedNative", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.UnmappedNative"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Native method has no Juno intrinsic")
                .hasMessageContaining("notAJunoIntrinsic");
    }
}
