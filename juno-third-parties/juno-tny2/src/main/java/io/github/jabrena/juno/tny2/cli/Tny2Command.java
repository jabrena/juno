package io.github.jabrena.juno.tny2.cli;

import java.io.IOException;
import java.io.PrintStream;

/** One {@code Tny2Builder} command, parsed and ready to run. */
public sealed interface Tny2Command permits TrainCommand, AskCommand, InspectCommand, ImageCommand {

    void run(PrintStream out) throws IOException;
}
