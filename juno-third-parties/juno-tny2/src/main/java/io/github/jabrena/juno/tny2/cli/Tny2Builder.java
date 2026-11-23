package io.github.jabrena.juno.tny2.cli;

import io.github.jabrena.juno.tny2.Tny2FormatException;

import java.io.IOException;

/**
 * Builds TNY2 models from facts, from the repository root:
 *
 * <pre>
 * ./mvnw -pl juno-third-parties/juno-tny2 compile
 * java -cp juno-third-parties/juno-tny2/target/classes io.github.jabrena.juno.tny2.cli.Tny2Builder \
 *     train juno-third-parties/juno-tny2/src/main/resources/tny2/facts target/tny2/TINY.BIN
 * </pre>
 *
 * Exits with 0 on success, 2 for wrong arguments and 1 for an unreadable or invalid file.
 */
public final class Tny2Builder {

    private Tny2Builder() {
    }

    public static void main(String[] args) {
        try {
            new CommandLine().parse(args).run(System.out);
        } catch (CommandLine.UsageException usage) {
            System.err.println(usage.getMessage());
            System.err.print(CommandLine.USAGE);
            System.exit(2);
        } catch (IOException | Tny2FormatException | IllegalArgumentException failed) {
            System.err.println(failed.getMessage());
            System.exit(1);
        }
    }
}
