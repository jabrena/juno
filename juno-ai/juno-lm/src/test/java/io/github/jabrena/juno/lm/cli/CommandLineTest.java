package io.github.jabrena.juno.lm.cli;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmModel;
import io.github.jabrena.juno.lm.JlmReader;
import io.github.jabrena.juno.lm.JlmWriter;
import io.github.jabrena.juno.lm.TrainedModels;
import io.github.jabrena.juno.lm.training.TrainingOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JlmBuilder command line")
class CommandLineTest {
    @TempDir
    private Path directory;

    private final CommandLine commandLine = new CommandLine();

    @Test
    @DisplayName("Parses train with the standard shape and default options")
    void shouldParseTrainWithDefaults() {
        // When
        JlmCommand command = commandLine.parse("train", "facts", "out.bin");

        // Then
        TrainingOptions defaults = TrainingOptions.defaults(2000);
        assertThat(command).isEqualTo(new TrainCommand(Path.of("facts"), Path.of("out.bin"), JlmConfig.standard(),
                defaults, 1, null, 0, null, 0));
    }

    @Test
    @DisplayName("Parses every train option")
    void shouldParseEveryTrainOption() {
        // When
        JlmCommand command = commandLine.parse("train", "--steps", "7", "facts", "--batch", "3", "--lr", "0.5",
                "--threads", "2", "--seed", "9", "--width", "16", "--layers", "2", "--heads", "4", "--hidden", "32",
                "--resume", "old.bin", "--log-every", "5", "--min-accuracy", "0.75", "--held-out", "held",
                "--min-held-out", "0.25", "--weight-decay", "0.05", "--dropout", "0.2", "--eval-every", "3", "out.bin");

        // Then
        TrainingOptions defaults = TrainingOptions.defaults(7);
        assertThat(command).isEqualTo(new TrainCommand(Path.of("facts"), Path.of("out.bin"),
                new JlmConfig(16, 2, 4, 32), new TrainingOptions(7, 3, 0.5f, defaults.finalLearningRate(),
                        defaults.warmupSteps(), defaults.clipNorm(), 2, 5, 0.05f, 0.2f, 3), 9, Path.of("old.bin"), 0.75,
                Path.of("held"), 0.25));
    }

    @Test
    @DisplayName("Parses ask, inspect and convert")
    void shouldParseAskInspectAndConvert() {
        // When & Then
        assertThat(commandLine.parse("ask", "m.bin", "Hello")).isEqualTo(new AskCommand(Path.of("m.bin"), "Hello", 0,
                1));
        assertThat(commandLine.parse("ask", "m.bin", "Hello", "--creativity", "4", "--seed", "3"))
                .isEqualTo(new AskCommand(Path.of("m.bin"), "Hello", 4, 3));
        assertThat(commandLine.parse("inspect", "m.bin")).isEqualTo(new InspectCommand(Path.of("m.bin")));
        assertThat(commandLine.parse("convert", "tiny.bin", "m.bin"))
                .isEqualTo(new ConvertCommand(Path.of("tiny.bin"), Path.of("m.bin")));
    }

    @Test
    @DisplayName("Explains what is wrong with the arguments")
    void shouldExplainWrongArguments() {
        // When & Then
        assertThatThrownBy(() -> commandLine.parse()).isInstanceOf(CommandLine.UsageException.class)
                .hasMessage("no command given");
        assertThatThrownBy(() -> commandLine.parse("fly")).hasMessage("unknown command: fly");
        assertThatThrownBy(() -> commandLine.parse("inspect")).hasMessage("inspect is missing an argument");
        assertThatThrownBy(() -> commandLine.parse("inspect", "m.bin", "--steps", "3"))
                .hasMessage("inspect does not take --steps");
        assertThatThrownBy(() -> commandLine.parse("ask", "m.bin", "q", "--seed")).hasMessage("--seed needs a value");
        assertThatThrownBy(() -> commandLine.parse("ask", "m.bin", "q", "--seed", "x"))
                .hasMessage("--seed must be a whole number, not x");
        assertThatThrownBy(() -> commandLine.parse("train", "f", "o", "--lr", "fast"))
                .hasMessage("--lr must be a number, not fast");
        assertThat(CommandLine.USAGE).contains("train", "ask", "inspect", "convert").doesNotContain("image");
    }

    @Test
    @DisplayName("Trains, inspects and asks a model end to end")
    void shouldTrainInspectAndAskAModel() throws Exception {
        // Given
        Path facts = directory.resolve("facts.txt");
        Files.writeString(facts, "Hello => Hi!\nWhat is Juno? => A compiler.\n");
        Path model = directory.resolve("out/model.bin");

        // When
        String trained = run("train", facts.toString(), model.toString(), "--steps", "150", "--batch", "8",
                "--dropout", "0", "--weight-decay", "0",
                "--lr", "0.01", "--threads", "2", "--width", "16", "--layers", "1", "--heads", "2", "--hidden", "32",
                "--log-every", "0");
        String inspected = run("inspect", model.toString());
        String answered = run("ask", model.toString(), "Hello");

        // Then
        assertThat(trained).startsWith("Training JlmConfig[width=16, layers=1, heads=2, hidden=32, context=128")
                .contains("on 2 facts: 150 steps of 8 on 2 threads", "2 of 2 questions answered exactly (100.0%)");
        assertThat(new JlmReader().read(model).config()).isEqualTo(new JlmConfig(16, 1, 2, 32));
        assertThat(inspected).contains("width 16, layers 1, heads 2, hidden 32, context 128, vocabulary 97",
                "PSRAM on the board: %d bytes of weights + %d bytes of attention cache"
                        .formatted(Files.size(model), 128 * (2 * 16 + 8)));
        assertThat(answered).isEqualTo("Hi!" + System.lineSeparator());
    }

    @Test
    @DisplayName("Converts a TNY2 file into a JLM1 file with the same weights")
    void shouldConvertATny2File() throws Exception {
        // Given
        JlmModel model = TrainedModels.model();
        byte[] jlm = new JlmWriter().toBytes(model);
        int header = model.config().headerSize();
        ByteBuffer tny2 = ByteBuffer.allocate(12 + jlm.length - header).order(ByteOrder.LITTLE_ENDIAN);
        tny2.put(new byte[] {'T', 'N', 'Y', '2'}).putShort((short) 32).putShort((short) 1)
                .putShort((short) 2)
                .putShort((short) 64).put(jlm, header, jlm.length - header);
        Path source = directory.resolve("TINY.BIN");
        Files.write(source, tny2.array());
        Path output = directory.resolve("converted/JUNO.BIN");

        // When
        String converted = run("convert", source.toString(), output.toString());

        // Then
        assertThat(Files.readAllBytes(output)).isEqualTo(jlm);
        assertThat(converted).contains("(%d bytes)".formatted(jlm.length));
    }

    @Test
    @DisplayName("Resumes training from an existing model and lists the questions it still misses")
    void shouldResumeFromAModelAndListMisses() throws Exception {
        // Given
        Path facts = directory.resolve("facts.txt");
        Files.writeString(facts, "Hello => Hi!\nWhat is Juno? => A Java compiler.\nWhich board? => Nano ESP32.\n"
                + "Who are you? => Someone new.\n");
        Path start = directory.resolve("start.bin");
        new JlmWriter().write(TrainedModels.model(), start);

        // When
        String output = run("train", facts.toString(), directory.resolve("next.bin").toString(), "--resume",
                start.toString(), "--steps", "1", "--threads", "1", "--log-every", "0", "--lr", "1e-6");

        // Then
        assertThat(output).startsWith("Training JlmConfig[width=32, layers=1, heads=2, hidden=64, context=128")
                .contains("  Who are you? -> ").contains("(expected Someone new.)");
    }

    @Test
    @DisplayName("Writes the model but fails when it answers fewer facts than --min-accuracy")
    void shouldFailBelowTheMinimumAccuracy() throws Exception {
        // Given
        Path facts = directory.resolve("facts.txt");
        Files.writeString(facts, "Hello => Hi!\nWho are you? => Someone new.\n");
        Path start = directory.resolve("start.bin");
        Path next = directory.resolve("next.bin");
        new JlmWriter().write(TrainedModels.model(), start);
        String[] args = {"train", facts.toString(), next.toString(), "--resume", start.toString(), "--steps", "1",
            "--threads", "1", "--log-every", "0", "--lr", "1e-6", "--min-accuracy", "1"};

        // When & Then
        assertThatThrownBy(() -> run(args)).isInstanceOf(IllegalStateException.class)
                .hasMessage("accuracy 50.0% is below the required 100.0%");
        assertThat(next).exists();
        assertThat(run(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9],
                args[10], args[11], args[12], args[13], "0.5")).contains("1 of 2 questions answered exactly");
        assertThatThrownBy(() -> new TrainCommand(facts, next, JlmConfig.standard(), TrainingOptions.defaults(1), 1,
                null, 1.5, null, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minAccuracy");
        assertThatThrownBy(() -> new TrainCommand(facts, next, JlmConfig.standard(), TrainingOptions.defaults(1), 1,
                null, -0.1, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrainCommand(facts, next, JlmConfig.standard(), TrainingOptions.defaults(1), 1,
                null, 0, null, 0.5)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("held-out");
        assertThatThrownBy(() -> new TrainCommand(facts, next, JlmConfig.standard(), TrainingOptions.defaults(1), 1,
                null, 0, facts, 2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minHeldOutAccuracy");
    }

    @Test
    @DisplayName("Scores held-out facts at each checkpoint, keeps the best model and gates on its held-out score")
    void shouldScoreHeldOutFactsAndGateOnThem() throws Exception {
        // Given
        Path facts = directory.resolve("facts.txt");
        Files.writeString(facts, "Hello => Hi!\nWhat is Juno? => A compiler.\n");
        Path heldOut = directory.resolve("heldout.txt");
        Files.writeString(heldOut, "Who made the moon? => Nobody here.\n");
        Path model = directory.resolve("model.bin");
        String[] args = {"train", facts.toString(), model.toString(), "--steps", "40", "--batch", "8", "--lr", "0.01",
            "--threads", "2", "--width", "16", "--layers", "1", "--heads", "2", "--hidden", "32", "--log-every", "0",
            "--held-out", heldOut.toString(), "--eval-every", "20", "--dropout", "0", "--weight-decay", "0"};

        // When
        String output = run(args);

        // Then
        assertThat(output).contains("step 20  held-out ", "step 40  held-out ", "Held-out: ", "best at step ");
        assertThat(model).exists();
        String[] gated = java.util.Arrays.copyOf(args, args.length + 2);
        gated[args.length] = "--min-held-out";
        gated[args.length + 1] = "1";
        assertThatThrownBy(() -> run(gated)).isInstanceOf(IllegalStateException.class)
                .hasMessage("held-out accuracy 0.0% is below the required 100.0%");
    }

    private static String run(String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CommandLine().parse(args).run(new PrintStream(out, true, StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8);
    }
}
