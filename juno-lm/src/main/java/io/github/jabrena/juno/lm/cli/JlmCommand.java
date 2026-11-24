package io.github.jabrena.juno.lm.cli;

import java.io.IOException;
import java.io.PrintStream;

/** One {@code JlmBuilder} command, parsed and ready to run. */
public sealed interface JlmCommand permits TrainCommand, AskCommand, InspectCommand, ConvertCommand {

    void run(PrintStream out) throws IOException;
}
