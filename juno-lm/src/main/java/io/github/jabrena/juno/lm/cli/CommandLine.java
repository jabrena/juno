package io.github.jabrena.juno.lm.cli;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.training.TrainingOptions;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses {@code JlmBuilder} arguments into a {@link JlmCommand}. */
public final class CommandLine {
    public static final String USAGE = """
            Usage: JlmBuilder <command> [arguments]
              train <facts file or directory> <model.bin> [--steps N] [--batch N] [--lr X] [--threads N]
                    [--seed N] [--width D] [--layers L] [--heads H] [--hidden N] [--resume model.bin]
                    [--log-every N] [--min-accuracy 0-1] [--held-out facts] [--min-held-out 0-1]
                    [--weight-decay X] [--dropout 0-1] [--eval-every N]
              ask <model.bin> <question> [--creativity 0-9] [--seed N]
              inspect <model.bin>
              convert <tny2 model> <model.bin>
            """;
    private static final String PREFIX = "--";
    private static final int DEFAULT_STEPS = 2000;
    private static final Map<String, Set<String>> OPTIONS = Map.of(
            "train", Set.of("steps", "batch", "lr", "threads", "seed", "width", "layers", "heads", "hidden",
                    "resume", "log-every", "min-accuracy", "held-out", "min-held-out", "weight-decay", "dropout",
                    "eval-every"),
            "ask", Set.of("creativity", "seed"),
            "inspect", Set.of(),
            "convert", Set.of());

    /** Thrown for arguments that do not form a command; its message says what is wrong. */
    public static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    public JlmCommand parse(String... args) {
        if (args.length == 0) {
            throw new UsageException("no command given");
        }
        String name = args[0];
        Set<String> allowed = OPTIONS.get(name);
        if (allowed == null) {
            throw new UsageException("unknown command: " + name);
        }
        Arguments arguments = new Arguments(name, allowed, args);
        return switch (name) {
            case "train" -> train(arguments);
            case "ask" -> new AskCommand(Path.of(arguments.positional(0)), arguments.positional(1),
                    arguments.integer("creativity", 0), arguments.integer("seed", 1));
            case "convert" -> new ConvertCommand(Path.of(arguments.positional(0)), Path.of(arguments.positional(1)));
            default -> new InspectCommand(Path.of(arguments.positional(0)));
        };
    }

    private static JlmCommand train(Arguments arguments) {
        JlmConfig shape = JlmConfig.standard();
        JlmConfig config = new JlmConfig(arguments.integer("width", shape.width()),
                arguments.integer("layers", shape.layers()), arguments.integer("heads", shape.heads()),
                arguments.integer("hidden", shape.hidden()));
        TrainingOptions defaults = TrainingOptions.defaults(arguments.integer("steps", DEFAULT_STEPS));
        TrainingOptions options = new TrainingOptions(defaults.steps(),
                arguments.integer("batch", defaults.batchSize()),
                arguments.decimal("lr", defaults.learningRate()), defaults.finalLearningRate(),
                defaults.warmupSteps(), defaults.clipNorm(), arguments.integer("threads", defaults.threads()),
                arguments.integer("log-every", defaults.logEvery()),
                arguments.decimal("weight-decay", defaults.weightDecay()),
                arguments.decimal("dropout", defaults.dropout()),
                arguments.integer("eval-every", defaults.evalEvery()));
        String resume = arguments.option("resume");
        String heldOut = arguments.option("held-out");
        return new TrainCommand(Path.of(arguments.positional(0)), Path.of(arguments.positional(1)), config, options,
                arguments.integer("seed", 1), resume == null ? null : Path.of(resume),
                arguments.decimal("min-accuracy", 0f), heldOut == null ? null : Path.of(heldOut),
                arguments.decimal("min-held-out", 0f));
    }

    /** Positional arguments and {@code --name value} options of one command. */
    private static final class Arguments {
        private final String command;
        private final List<String> positional = new ArrayList<>();
        private final Map<String, String> options = new HashMap<>();

        Arguments(String command, Set<String> allowed, String[] args) {
            this.command = command;
            for (int i = 1; i < args.length; i++) {
                if (!args[i].startsWith(PREFIX)) {
                    positional.add(args[i]);
                    continue;
                }
                String name = args[i].substring(PREFIX.length());
                if (!allowed.contains(name)) {
                    throw new UsageException("%s does not take --%s".formatted(command, name));
                }
                if (i + 1 >= args.length) {
                    throw new UsageException("--%s needs a value".formatted(name));
                }
                options.put(name, args[++i]);
            }
        }

        String positional(int index) {
            if (index >= positional.size()) {
                throw new UsageException("%s is missing an argument".formatted(command));
            }
            return positional.get(index);
        }

        @Nullable String option(String name) {
            return options.get(name);
        }

        int integer(String name, int fallback) {
            String value = options.get(name);
            try {
                return value == null ? fallback : Integer.parseInt(value);
            } catch (NumberFormatException invalid) {
                throw new UsageException("--%s must be a whole number, not %s".formatted(name, value));
            }
        }

        float decimal(String name, float fallback) {
            String value = options.get(name);
            try {
                return value == null ? fallback : Float.parseFloat(value);
            } catch (NumberFormatException invalid) {
                throw new UsageException("--%s must be a number, not %s".formatted(name, value));
            }
        }
    }
}
