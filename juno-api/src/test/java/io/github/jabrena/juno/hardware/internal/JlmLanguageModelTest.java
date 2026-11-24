package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Java engine over heap memory whose kernels match the board's C kernels, running the bundled JUNO.BIN. The
 * expected answers are what {@code JlmInference} in {@code juno-lm} answers, so the board engine and the reference
 * engine stay the same computation.
 */
@DisplayName("JLM1 language model engine")
class JlmLanguageModelTest {
    private static final Path BUNDLED = Path.of("../juno-lm/src/main/resources/lm/JUNO.BIN");
    private static final int UNLIMITED = Integer.MAX_VALUE;
    private static byte[] model;

    @BeforeAll
    static void readModel() throws IOException {
        model = Files.readAllBytes(BUNDLED);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "What is Juno?|Juno compiles Java programs into firmware for microcontrollers.",
        "'  WHAT is   juno?!'|Juno compiles Java programs into firmware for microcontrollers.",
        "Which JDK does Juno need?|JDK 27.",
        "Which board has PSRAM?|The Nano ESP32 and the AI Thinker ESP32-CAM.",
        "Hola, que tal?|I do not know."})
    @DisplayName("Answers greedily exactly as the reference engine")
    void shouldAnswerAsTheReferenceEngine(String question, String expected) {
        // Given
        JlmLanguageModel engine = loaded(1);

        // When
        ModelStatus status = engine.ask(question, 0);

        // Then
        assertThat(status).isEqualTo(ModelStatus.OK);
        assertThat(answer(engine)).isEqualTo(expected);
        assertThat(engine.nextChar()).isEqualTo(LanguageModel.END);
    }

    @ParameterizedTest(name = "seed {0}")
    @CsvSource(delimiter = '|', value = {
        "2|Small boards cannot hold a JVM; Juno compiles Java instead.",
        "5|Juno compiles Java programs into firmware for microcontrollers."})
    @DisplayName("Samples exactly as the reference engine for the same random sequence")
    void shouldSampleAsTheReferenceEngine(long seed, String expected) {
        // Given
        JlmLanguageModel engine = loaded(seed);

        // When
        engine.ask("What is Juno?", LanguageModel.MAX_CREATIVITY);

        // Then
        assertThat(answer(engine)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Asks with the bytes of a request body as with a String, up to the length or a zero byte")
    void shouldAskWithBytes() {
        // Given
        JlmLanguageModel engine = loaded(1);
        byte[] body = "Which JDK does Juno need?\0ignored".getBytes(StandardCharsets.US_ASCII);
        byte[] cut = Arrays.copyOf("Which JDK does Juno need? and more".getBytes(StandardCharsets.US_ASCII), 64);

        // When & Then
        assertThat(engine.ask(body, body.length, 0)).isEqualTo(ModelStatus.OK);
        assertThat(answer(engine)).isEqualTo("JDK 27.");
        assertThat(engine.ask(cut, "Which JDK does Juno need?".length(), 0)).isEqualTo(ModelStatus.OK);
        assertThat(answer(engine)).isEqualTo("JDK 27.");
        assertThat(engine.ask(body, body.length + 1, 0)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
        assertThat(engine.ask(body, -1, 0)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Reports its shape while loaded and frees the model and the cache on release")
    void shouldReportItsShapeAndRelease() {
        // Given
        ArrayJlmRuntime runtime = new ArrayJlmRuntime(model, UNLIMITED, 1);
        JlmLanguageModel engine = new JlmLanguageModel(runtime);

        // When
        ModelStatus status = engine.begin();

        // Then
        assertThat(status).isEqualTo(ModelStatus.OK);
        assertThat(engine.isLoaded()).isTrue();
        assertThat(engine.width()).isEqualTo(96);
        assertThat(engine.layers()).isEqualTo(4);
        assertThat(runtime.blocks).hasSize(2);
        assertThat(runtime.blocks.get(1).size()).isEqualTo(2 * 4 * 128 * (4 + 96));
        engine.release();
        assertThat(runtime.blocks).allMatch(ArrayModelMemory::released);
        assertThat(engine.isLoaded()).isFalse();
        assertThat(engine.width()).isZero();
        assertThat(engine.layers()).isZero();
        assertThat(engine.ask("What is Juno?", 0)).isEqualTo(ModelStatus.NOT_LOADED);
        assertThat(engine.nextChar()).isEqualTo(LanguageModel.END);
    }

    @Test
    @DisplayName("Rejects a creativity out of range and a question with nothing to read")
    void shouldRejectBadQuestions() {
        // Given
        JlmLanguageModel engine = loaded(1);

        // When & Then
        assertThat(engine.ask("Hello", -1)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
        assertThat(engine.ask("Hello", LanguageModel.MAX_CREATIVITY + 1)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
        assertThat(engine.ask(" ?! ,; ", 0)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
        assertThat(engine.ask("\u0001é", 0)).isEqualTo(ModelStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Stops a long answer at the end of the context")
    void shouldStopAtTheEndOfTheContext() {
        // Given
        JlmLanguageModel engine = loaded(5);

        // When
        engine.ask("x".repeat(200), LanguageModel.MAX_CREATIVITY);

        // Then
        assertThat(answer(engine)).hasSizeLessThanOrEqualTo(128 - LanguageModel.MAX_QUESTION - 1);
    }

    @Test
    @DisplayName("Runs out of memory without a bundled copy or room for the cache, freeing what it took")
    void shouldReportExhaustedMemory() {
        // Given
        JlmLanguageModel noBundle = new JlmLanguageModel(new ArrayJlmRuntime(null, UNLIMITED, 1));
        ArrayJlmRuntime tight = new ArrayJlmRuntime(model, 1000, 1);
        JlmLanguageModel noCache = new JlmLanguageModel(tight);

        // When & Then
        assertThat(noBundle.begin()).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(noCache.begin()).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(tight.blocks).singleElement().matches(ArrayModelMemory::released);
        assertThat(noCache.isLoaded()).isFalse();
    }

    @Test
    @DisplayName("Refuses a file that is not a complete JLM1 model the engine accepts")
    void shouldRefuseInvalidModels() {
        // When & Then
        assertThat(begin(corrupt(0, 'T'))).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(3, '2'))).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(Arrays.copyOf(model, model.length - 1))).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(Arrays.copyOf(model, 10))).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(6, 9))).as("nine layers").isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(12, 73))).as("context below the longest question").isEqualTo(
                ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(14, 2))).as("no characters").isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(17, ' '))).as("a repeated space").isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(16, 'X'))).as("no space").isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(begin(corrupt(17, 0x7F))).as("not printable").isEqualTo(ModelStatus.INVALID_MODEL);
    }

    @Test
    @DisplayName("Loads a model file from an SD card, and reports a missing, empty or short one as unreadable")
    void shouldLoadAModelFromAnSdCard() {
        // Given
        ArrayJlmRuntime runtime = new ArrayJlmRuntime(null, UNLIMITED, 1);
        JlmLanguageModel engine = new JlmLanguageModel(runtime);

        // When & Then
        assertThat(engine.begin(card(model.length, model), "/JUNO.BIN")).isEqualTo(ModelStatus.OK);
        engine.ask("Which JDK does Juno need?", 0);
        assertThat(answer(engine)).isEqualTo("JDK 27.");
        assertThat(engine.begin(card(-1, model), "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
        assertThat(engine.isLoaded()).isFalse();
        assertThat(engine.begin(card(0, model), "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
        assertThat(engine.begin(card(model.length, new byte[10]), "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
        assertThat(engine.begin(card(model.length, (InputStream) null), "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
        assertThat(new JlmLanguageModel(new ArrayJlmRuntime(null, 10, 1)).begin(card(model.length, model),
                "/JUNO.BIN")).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(runtime.blocks).filteredOn(block -> !block.released()).isEmpty();
    }

    @Test
    @DisplayName("Reports a card that fails while the model is read as unreadable")
    void shouldReportAFailingCardAsUnreadable() {
        // Given
        JlmLanguageModel engine = new JlmLanguageModel(new ArrayJlmRuntime(null, UNLIMITED, 1));
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("card removed");
            }
        };

        // When & Then
        assertThat(engine.begin(card(model.length, failing), "/JUNO.BIN")).isEqualTo(ModelStatus.UNREADABLE);
    }

    private static JlmLanguageModel loaded(long seed) {
        JlmLanguageModel engine = new JlmLanguageModel(new ArrayJlmRuntime(model, UNLIMITED, seed));
        assertThat(engine.begin()).isEqualTo(ModelStatus.OK);
        return engine;
    }

    private static ModelStatus begin(byte[] file) {
        return new JlmLanguageModel(new ArrayJlmRuntime(file, UNLIMITED, 1)).begin();
    }

    private static byte[] corrupt(int offset, int value) {
        byte[] copy = model.clone();
        copy[offset] = (byte) value;
        return copy;
    }

    private static String answer(LanguageModel engine) {
        StringBuilder answer = new StringBuilder();
        for (int c = engine.nextChar(); c != LanguageModel.END; c = engine.nextChar()) {
            answer.append((char) c);
        }
        return answer.toString();
    }

    private static SdCard card(int size, byte[] content) {
        return card(size, new ByteArrayInputStream(content));
    }

    /** A card whose files all have {@code size} bytes and open as {@code content}. */
    private static SdCard card(int size, InputStream content) {
        return (SdCard) Proxy.newProxyInstance(SdCard.class.getClassLoader(), new Class<?>[] {SdCard.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "size" -> size;
                    case "open" -> content;
                    default -> null;
                });
    }
}
