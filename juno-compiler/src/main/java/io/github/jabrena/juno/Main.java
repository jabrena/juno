package io.github.jabrena.juno;

import java.util.List;

/** The {@code juno} command line: {@code compile} and {@code inspect} (see {@link CliCommand}). */
public final class Main {
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
        CliCommand.parse(List.of(args)).run(System.out);
    }
}
