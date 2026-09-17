package io.github.jabrena.juno;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Main {
    private static final String VERSION = "0.1.0-SNAPSHOT";

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (CompileException exception) {
            System.err.println("juno: " + exception.getMessage());
            System.exit(2);
        }
    }

    static void run(String[] args) {
        if (args.length == 0 || Arrays.asList(args).contains("--help") || Arrays.asList(args).contains("-h")) {
            System.out.print(usage());
            return;
        }
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("Juno " + VERSION);
            return;
        }
        if (!args[0].equals("compile")) {
            throw new CompileException("Unknown command '" + args[0] + "'. Run with --help for usage.");
        }

        String mainClass = null;
        String classPathValue = "target/classes";
        Path output = null;
        for (int index = 1; index < args.length; index++) {
            String option = args[index];
            if (option.equals("--board")) {
                String board = value(args, ++index, option);
                if (!board.equals("uno-r4-wifi") && !board.equals("uno-r4-minima")) {
                    throw new CompileException("Unsupported board '" + board + "'; use uno-r4-wifi or uno-r4-minima");
                }
            } else if (option.equals("--main")) {
                mainClass = value(args, ++index, option);
            } else if (option.equals("--classpath") || option.equals("-cp")) {
                classPathValue = value(args, ++index, option);
            } else if (option.equals("--output") || option.equals("-o")) {
                output = Path.of(value(args, ++index, option));
            } else {
                throw new CompileException("Unknown option '" + option + "'");
            }
        }
        if (mainClass == null) {
            throw new CompileException("Missing required option --main");
        }
        if (output == null) {
            String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
            output = Path.of("build", "juno", simpleName, simpleName + ".ino");
        }
        List<Path> classPath = new ArrayList<>();
        for (String entry : classPathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                classPath.add(Path.of(entry));
            }
        }
        if (classPath.isEmpty()) {
            throw new CompileException("Classpath is empty");
        }

        new JunoCompiler().compileTo(classPath, mainClass, output);
        System.out.println("Generated " + output + " for Arduino UNO R4");
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new CompileException("Missing value for " + option);
        }
        return args[index];
    }

    private static String usage() {
        return """
                Juno - Java AOT compiler for Arduino UNO R4

                Usage:
                  java -jar juno.jar compile --main <class> [options]

                Options:
                  --classpath, -cp <paths>  Class directories or JARs (default: target/classes)
                  --output, -o <file>       Generated .ino file (default: build/juno/<Main>/<Main>.ino)
                  --board <board>           uno-r4-wifi or uno-r4-minima
                  --help, -h                Show this help
                  --version                 Show the version
                """;
    }
}
