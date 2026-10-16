package io.github.jabrena.juno;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The options every command shares: which program to compile, where its classes are, and for which board.
 *
 * @param mainClass the entry point's fully qualified class name
 * @param classPath the closed-world classpath
 * @param board the target board, required when the entry point has no {@code @Board} annotation and otherwise
 *     required only when that annotation declares more than one board
 */
record ProgramSelection(String mainClass, List<Path> classPath, Optional<String> board) {
    ProgramSelection {
        classPath = List.copyOf(classPath);
    }

    String simpleName() {
        return mainClass.substring(mainClass.lastIndexOf('.') + 1);
    }

    /** {@code build/juno/<Main>/<Main>.S}, where {@code juno compile} writes when no {@code --output} is given. */
    Path defaultAssemblyOutput() {
        return Path.of("build", "juno", simpleName(), simpleName() + ".S");
    }

    /** Collects {@code --main}, {@code --classpath} and {@code --board} while a command parses its own options. */
    static final class Parser {
        private static final String DEFAULT_CLASS_PATH = "target/classes";

        private @Nullable String mainClass;
        private String classPath = DEFAULT_CLASS_PATH;
        private Optional<String> board = Optional.empty();

        /** Consumes {@code option} (and its value) when it is a shared option; otherwise leaves it to the caller. */
        boolean accept(String option, CommandLineArguments arguments) {
            switch (option) {
                case "--main" -> mainClass = arguments.valueFor(option);
                case "--classpath", "-cp" -> classPath = arguments.valueFor(option);
                case "--board" -> board = Optional.of(arguments.valueFor(option));
                default -> {
                    return false;
                }
            }
            return true;
        }

        ProgramSelection build() {
            if (mainClass == null) {
                throw new CompileException("Missing required option --main");
            }
            return new ProgramSelection(mainClass, parseClassPath(classPath), board);
        }

        private List<Path> parseClassPath(String value) {
            List<Path> entries = Arrays.stream(value.split(Pattern.quote(File.pathSeparator)))
                    .filter(entry -> !entry.isBlank())
                    .map(Path::of)
                    .toList();
            if (entries.isEmpty()) {
                throw new CompileException("Classpath is empty");
            }
            return entries;
        }
    }
}
