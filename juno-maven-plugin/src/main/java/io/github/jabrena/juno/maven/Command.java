package io.github.jabrena.juno.maven;

import java.util.List;

/**
 * One external command line to run.
 *
 * @param arguments the program followed by its arguments
 * @param mode whether the command's output is captured or the command takes over the terminal
 */
record Command(List<String> arguments, Mode mode) {
    enum Mode {
        /** Runs to completion; its combined stdout/stderr is captured. */
        CAPTURED,
        /** Inherits the terminal and runs until the user interrupts it, like a serial monitor. */
        INTERACTIVE
    }

    Command {
        arguments = List.copyOf(arguments);
    }

    String program() {
        return arguments.getFirst();
    }

    boolean isInteractive() {
        return mode == Mode.INTERACTIVE;
    }

    /** The command line as a user would type it. */
    String display() {
        return String.join(" ", arguments);
    }
}
