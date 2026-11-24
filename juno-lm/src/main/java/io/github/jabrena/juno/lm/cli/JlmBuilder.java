package io.github.jabrena.juno.lm.cli;

import io.github.jabrena.juno.lm.JlmFormatException;

import java.io.IOException;

/**
 * Builds JLM1 models from facts, from the repository root:
 *
 * <pre>
 * ./mvnw -pl juno-lm compile
 * java -cp juno-lm/target/classes io.github.jabrena.juno.lm.cli.JlmBuilder \
 *     train juno-lm/src/main/resources/lm/facts target/lm/JUNO.BIN
 * </pre>
 *
 * Exits with 0 on success, 2 for wrong arguments and 1 for an unreadable or invalid file, or a trained model below
 * {@code --min-accuracy}. Maven runs the same {@code train} on demand with
 * {@code ./mvnw -pl juno-lm -Plm-model process-classes}.
 */
public final class JlmBuilder {

    private JlmBuilder() {
    }

    public static void main(String[] args) {
        try {
            new CommandLine().parse(args).run(System.out);
        } catch (CommandLine.UsageException usage) {
            System.err.println(usage.getMessage());
            System.err.print(CommandLine.USAGE);
            System.exit(2);
        } catch (IOException | JlmFormatException | IllegalArgumentException | IllegalStateException failed) {
            System.err.println(failed.getMessage());
            System.exit(1);
        }
    }
}
