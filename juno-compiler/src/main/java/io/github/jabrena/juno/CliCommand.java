package io.github.jabrena.juno;

import java.io.PrintStream;
import java.util.List;

/** One parsed invocation of the {@code juno} command line, ready to run. */
sealed interface CliCommand permits CliCommand.Help, CliCommand.Version, CompileCommand, InspectCommand {
    void run(PrintStream out);

    /** Parses {@code args}: a help or version flag, or a {@code compile}/{@code inspect} command and its options. */
    static CliCommand parse(List<String> args) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            return new Help();
        }
        if (args.equals(List.of("--version"))) {
            return new Version();
        }
        CommandLineArguments options = new CommandLineArguments(args.subList(1, args.size()));
        return switch (args.getFirst()) {
            case "compile" -> CompileCommand.parse(options);
            case "inspect" -> InspectCommand.parse(options);
            default -> throw new CompileException("Unknown command '" + args.getFirst()
                    + "'. Run with --help for usage.");
        };
    }

    record Help() implements CliCommand {
        private static final String USAGE = """
                Juno - Java AOT compiler for Arduino UNO R3, UNO R4, UNO Q and UNO WiFi Rev2

                Usage:
                  java -jar juno.jar compile --main <class> [options]
                  java -jar juno.jar inspect --main <class> [options]

                compile options:
                  --classpath, -cp <paths>  Class directories or JARs (default: target/classes)
                  --output, -o <file>       Generated .S file (default: build/juno/<Main>/<Main>.S)
                  --board <id>              Target board (arduino-uno-r3, arduino-uno-r4-wifi, arduino-uno-q,
                                            arduino-uno-r3-wifi-rev2); required only when
                                            @Board declares more than one board
                  --gc-log                  Have the generated garbage collector print one Serial
                                            line per collection (arena bytes used before/after),
                                            visible via juno:monitor. Off by default: costs no
                                            extra flash/RAM/time when omitted.
                  --metrics                 Print deterministic generated-code counts and fixed-frame sizes.
                                            Use the final ELF and target hardware for size/cycle measurements.
                  Also emits <Main>Shim.cpp and the matching <Main>.ino wrapper alongside it.
                  Runtime-risk resource estimates and structured findings are printed automatically
                  afterward (also true of juno-maven-plugin's compile/verify/upload goals).

                The target board is read from the entry-point class's @Board annotation
                (io.github.jabrena.juno.annotations.Board); a class with no @Board annotation targets the
                board --board names, or the UNO R4 WiFi if --board is omitted. @Board may declare more
                than one board (e.g. @Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})), in which case
                --board picks which one this build compiles for.

                inspect options:
                  --classpath, -cp <paths>  Class directories or JARs (default: target/classes)
                  --board <id>              Target board (arduino-uno-r3, arduino-uno-r4-wifi, arduino-uno-q,
                                            arduino-uno-r3-wifi-rev2); required only when
                                            @Board declares more than one board
                  --ir                      Print the lowered/optimized Juno IR per reachable method
                  --cfg                     Print each reachable method's basic-block control flow graph

                Global options:
                  --help, -h                Show this help
                  --version                 Show the version
                """;

        @Override
        public void run(PrintStream out) {
            out.print(USAGE);
        }
    }

    record Version() implements CliCommand {
        private static final String VERSION = "0.1.0-SNAPSHOT";

        @Override
        public void run(PrintStream out) {
            out.println("Juno " + VERSION);
        }
    }
}
