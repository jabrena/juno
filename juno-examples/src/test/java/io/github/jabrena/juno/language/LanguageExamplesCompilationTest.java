package io.github.jabrena.juno.language;

import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compiles every {@code language} example entry point through {@link JunoCompiler}, once per board its
 * {@code @Board} annotation declares (UNO R4 WiFi when it declares none). This is the in-process counterpart of
 * {@code juno:verify}: it catches a language example the compiler rejects without needing Docker or arduino-cli.
 *
 * <p>Entry points are found by reading their class files, not by loading them: {@code ScopedValuesPrecision} is
 * compiled with preview features, which the test JVM does not enable, so loading it would fail. The Kotlin examples
 * under {@code language.kotlin} and {@code language.scala} are included: their {@code main} is found the same way.
 */
class LanguageExamplesCompilationTest {
    private static final String LANGUAGE_PACKAGE = "io/github/jabrena/juno/language";
    private static final String BOARD_ANNOTATION = "Lio/github/jabrena/juno/annotations/Board;";
    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("entryPoints")
    void compilesForEachDeclaredBoard(String mainClass, Board board) throws Exception {
        CompilationResult result = compile(mainClass, board);

        assertThat(result.assembly()).isNotBlank();
        assertThat(result.entryPointSymbol()).isNotBlank();
    }

    @Test
    void unsupportedFeatureIsRejectedWithADiagnostic() throws Exception {
        assertThatThrownBy(() -> compile(UnsupportedFeature.class.getName(), Board.UNO_R4_WIFI))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("toString");
    }

    /** Every language entry point except {@link UnsupportedFeature}, paired with each board it targets. */
    static Stream<Arguments> entryPoints() throws IOException, URISyntaxException {
        List<Arguments> arguments = new ArrayList<>();
        Path classesRoot = examplesRoot();
        try (Stream<Path> files = Files.walk(classesRoot.resolve(LANGUAGE_PACKAGE))) {
            for (Path file : files.filter(LanguageExamplesCompilationTest::isEntryPointCandidate).sorted().toList()) {
                ClassModel model = ClassFile.of().parse(file);
                if (!hasPublicStaticMain(model)) {
                    continue;
                }
                String mainClass = className(classesRoot, file);
                if (mainClass.equals(UnsupportedFeature.class.getName())) {
                    continue;
                }
                for (Board board : boardsFor(model)) {
                    arguments.add(Arguments.of(mainClass, board));
                }
            }
        }
        return arguments.stream();
    }

    private static boolean isEntryPointCandidate(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".class") && !name.equals("package-info.class") && !name.contains("$");
    }

    private static String className(Path classesRoot, Path file) {
        String relative = classesRoot.relativize(file).toString();
        return relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
    }

    private static boolean hasPublicStaticMain(ClassModel model) {
        return model.methods().stream().anyMatch(LanguageExamplesCompilationTest::isPublicStaticMain);
    }

    private static boolean isPublicStaticMain(MethodModel method) {
        return method.methodName().equalsString("main")
                && method.methodType().equalsString(MAIN_DESCRIPTOR)
                && method.flags().has(AccessFlag.PUBLIC)
                && method.flags().has(AccessFlag.STATIC);
    }

    /** The boards named by {@code @Board}, or UNO R4 WiFi when the entry point declares none. */
    private static List<Board> boardsFor(ClassModel model) {
        Optional<Annotation> board = model.findAttribute(Attributes.runtimeVisibleAnnotations())
                .flatMap(attribute -> attribute.annotations().stream()
                        .filter(annotation -> annotation.classSymbol().descriptorString().equals(BOARD_ANNOTATION))
                        .findFirst());
        if (board.isEmpty()) {
            return List.of(Board.UNO_R4_WIFI);
        }
        List<Board> boards = new ArrayList<>();
        for (AnnotationElement element : board.get().elements()) {
            if (element.value() instanceof AnnotationValue.OfArray array) {
                for (AnnotationValue value : array.values()) {
                    boards.add(boardOf(((AnnotationValue.OfClass) value).classSymbol()));
                }
            }
        }
        return boards;
    }

    private static Board boardOf(ClassDesc boardClass) {
        String descriptor = boardClass.descriptorString();
        return Board.fromApiClassName(descriptor.substring(1, descriptor.length() - 1));
    }

    private static CompilationResult compile(String mainClass, Board board) throws URISyntaxException {
        Path apiRoot = Path.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new JunoCompiler().compile(new CompilationRequest(List.of(examplesRoot(), apiRoot), mainClass,
                false, Optional.of(board.id())));
    }

    private static Path examplesRoot() throws URISyntaxException {
        return Path.of(UnsupportedFeature.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
