package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.classfile.ClassPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mainClassNotFoundOnTheClasspathFails() throws Exception {
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

        assertThatThrownBy(() -> new Linker().link(classes, "demo.DoesNotExist", Optional.of(Board.UNO_R4_WIFI.id())))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Main class not found on the classpath: demo.DoesNotExist");
    }

    @Test
    void aClassWithNoMainMethodFails() throws Exception {
        String source = """
                package demo;
                public final class NoMain {
                    static void notMain() {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoMain", source);

        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.NoMain"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Main class must declare static void main(String[]) or static void main()");
    }

    @Test
    void aNonStaticMainMethodFails() throws Exception {
        String source = """
                package demo;
                public final class InstanceMain {
                    public void main(String[] args) {
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.InstanceMain", source);

        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.InstanceMain"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Main class must declare static void main(String[]) or static void main()");
    }

    @Test
    void aReachableNativeMethodWithNoJunoIntrinsicFails() throws Exception {
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

        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.UnmappedNative"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Native method has no Juno intrinsic")
                .hasMessageContaining("notAJunoIntrinsic");
    }
}
