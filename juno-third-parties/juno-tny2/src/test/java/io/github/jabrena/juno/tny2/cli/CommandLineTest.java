package io.github.jabrena.juno.tny2.cli;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Reader;
import io.github.jabrena.juno.tny2.Tny2Writer;
import io.github.jabrena.juno.tny2.TrainedModels;
import io.github.jabrena.juno.tny2.training.TrainingOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Tny2Builder command line")
class CommandLineTest {
    @TempDir
    private Path directory;

    private final CommandLine commandLine = new CommandLine();

    @Test
    @DisplayName("Parses train with the TINY 2.1 shape and default options")
    void shouldParseTrainWithDefaults() {
        // When
        Tny2Command command = commandLine.parse("train", "facts", "out.bin");

        // Then
        TrainingOptions defaults = TrainingOptions.defaults(2000);
        assertThat(command).isEqualTo(new TrainCommand(Path.of("facts"), Path.of("out.bin"), Tny2Config.tiny21(),
                defaults, 1, null));
    }

    @Test
    @DisplayName("Parses every train option")
    void shouldParseEveryTrainOption() {
        // When
        Tny2Command command = commandLine.parse("train", "--steps", "7", "facts", "--batch", "3", "--lr", "0.5",
                "--threads", "2", "--seed", "9", "--width", "16", "--layers", "2", "--heads", "4", "--hidden", "32",
                "--resume", "old.bin", "--log-every", "5", "out.bin");

        // Then
        TrainingOptions defaults = TrainingOptions.defaults(7);
        assertThat(command).isEqualTo(new TrainCommand(Path.of("facts"), Path.of("out.bin"),
                new Tny2Config(16, 2, 4, 32), new TrainingOptions(7, 3, 0.5f, defaults.finalLearningRate(),
                        defaults.warmupSteps(), defaults.clipNorm(), 2, 5), 9, Path.of("old.bin")));
    }

    @Test
    @DisplayName("Parses ask, inspect and image")
    void shouldParseAskInspectAndImage() {
        // When & Then
        assertThat(commandLine.parse("ask", "m.bin", "Hello")).isEqualTo(new AskCommand(Path.of("m.bin"), "Hello", 0,
                1));
        assertThat(commandLine.parse("ask", "m.bin", "Hello", "--creativity", "4", "--seed", "3"))
                .isEqualTo(new AskCommand(Path.of("m.bin"), "Hello", 4, 3));
        assertThat(commandLine.parse("inspect", "m.bin")).isEqualTo(new InspectCommand(Path.of("m.bin")));
        assertThat(commandLine.parse("image", "m.bin", "Out.java"))
                .isEqualTo(new ImageCommand(Path.of("m.bin"), Path.of("Out.java")));
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
        assertThat(CommandLine.USAGE).contains("train", "ask", "inspect", "image");
    }

    @Test
    @DisplayName("Trains, inspects, asks and images a model end to end")
    void shouldTrainInspectAskAndImageAModel() throws Exception {
        // Given
        Path facts = directory.resolve("facts.txt");
        Files.writeString(facts, "Hello => Hi!\nWhat is Juno? => A compiler.\n");
        Path model = directory.resolve("out/model.bin");

        // When
        String trained = run("train", facts.toString(), model.toString(), "--steps", "60", "--batch", "8",
                "--lr", "0.01", "--threads", "2", "--width", "16", "--layers", "1", "--heads", "2", "--hidden", "32",
                "--log-every", "0");
        String inspected = run("inspect", model.toString());
        String answered = run("ask", model.toString(), "Hello");
        String imaged = run("image", model.toString(), directory.resolve("Tny2ModelImage.java").toString());

        // Then
        assertThat(trained).startsWith("Training Tny2Config[width=16, layers=1, heads=2, hidden=32]")
                .contains("on 2 facts: 60 steps of 8 on 2 threads", "2 of 2 questions answered exactly (100.0%)");
        assertThat(new Tny2Reader().read(model).config()).isEqualTo(new Tny2Config(16, 1, 2, 32));
        assertThat(inspected).contains("width 16, layers 1, heads 2, hidden 32",
                "PSRAM on the board: %d bytes of weights + %d bytes of attention cache"
                        .formatted(Files.size(model), 128 * (2 * 16 + 8)));
        assertThat(answered).isEqualTo("Hi!" + System.lineSeparator());
        assertThat(imaged).startsWith("Wrote ").contains(Files.size(model) + " model bytes");
        assertThat(directory.resolve("Tny2ModelImage.java")).exists();
    }

    @Test
    @DisplayName("Resumes training from an existing model and lists the questions it still misses")
    void shouldResumeFromAModelAndListMisses() throws Exception {
        // Given
        Path facts = directory.resolve("facts.txt");
        Files.writeString(facts, "Hello => Hi!\nWhat is Juno? => A Java compiler.\nWhich board? => Nano ESP32.\n"
                + "Who are you? => Someone new.\n");
        Path start = directory.resolve("start.bin");
        new Tny2Writer().write(TrainedModels.model(), start);

        // When
        String output = run("train", facts.toString(), directory.resolve("next.bin").toString(), "--resume",
                start.toString(), "--steps", "1", "--threads", "1", "--log-every", "0", "--lr", "1e-6");

        // Then
        assertThat(output).startsWith("Training Tny2Config[width=32, layers=1, heads=2, hidden=64]")
                .contains("  Who are you? -> ").contains("(expected Someone new.)");
    }

    private static String run(String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CommandLine().parse(args).run(new PrintStream(out, true, StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8);
    }
}
