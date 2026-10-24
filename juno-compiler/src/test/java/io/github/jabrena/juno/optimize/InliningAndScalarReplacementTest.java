package io.github.jabrena.juno.optimize;


import io.github.jabrena.juno.CompilerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Which calls the inliner removes and which allocations scalar replacement removes, compiled from real Java. */
@DisplayName("Inlining and scalar replacement")
class InliningAndScalarReplacementTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Inlines a small leaf helper with a branch")
    void shouldInlineASmallLeafHelperWithABranch() throws Exception {
        // Given
        String assembly = compile("""
                static int clamp(int value) { return value < 0 ? 0 : value > 99 ? 99 : value; }
                public static void main(String[] args) { Delay.millis(clamp(Random.nextInt(1024))); }
                """);

        // When & Then
        assertThat(assembly).doesNotContain("bl juno_fn");
    }

    @Test
    @DisplayName("Keeps a call to a method that calls something else")
    void shouldKeepACallToAMethodThatCallsSomethingElse() throws Exception {
        // Given
        String assembly = compile("""
                static void blink(int pin) { ArduinoUno.getGpio().digitalWrite(pin, true); Delay.millis(5); }
                public static void main(String[] args) { blink(13); blink(12); }
                """);

        // When & Then
        assertThat(assembly).contains("bl juno_fn");
    }

    @Test
    @DisplayName("Keeps a call to a method too large to inline")
    void shouldKeepACallToAMethodTooLargeToInline() throws Exception {
        // Given
        String assembly = compile("""
                static int mix(int a, int b) {
                    return (a * 3 + b) ^ (a >> 2) ^ (b << 5) ^ (a - b) ^ (a | b) ^ (a & 7) ^ (b % 11);
                }
                public static void main(String[] args) { Delay.millis(mix(Random.nextInt(1024), Random.nextInt(1024))); }
                """);

        // When & Then
        assertThat(assembly).contains("bl juno_fn");
    }

    @Test
    @DisplayName("Keeps a call with a long parameter")
    void shouldKeepACallWithALongParameter() throws Exception {
        // Given
        String assembly = compile("""
                static int low(long value) { return (int) value; }
                public static void main(String[] args) { Delay.millis(low(Random.nextInt(1024))); }
                """);

        // When & Then
        assertThat(assembly).contains("bl juno_fn");
    }

    @Test
    @DisplayName("Replaces a record that never leaves the method")
    void shouldReplaceARecordThatNeverLeavesTheMethod() throws Exception {
        // Given
        String assembly = compile("""
                record Pair(int left, int right) {}
                public static void main(String[] args) {
                    int total = 0;
                    for (int i = 0; i < 10; i++) {
                        Pair pair = new Pair(i, Random.nextInt(1024));
                        total += pair.left() * pair.right();
                    }
                    Delay.millis(total);
                }
                """);

        // When & Then
        assertThat(assembly).doesNotContain("bl juno_alloc", "bl juno_fn");
    }

    @Test
    @DisplayName("Keeps an object stored in a static field")
    void shouldKeepAnObjectStoredInAStaticField() throws Exception {
        // Given
        String assembly = compile("""
                record Pair(int left, int right) {}
                static Pair last;
                public static void main(String[] args) {
                    Pair pair = new Pair(1, Random.nextInt(1024));
                    last = pair;
                    Delay.millis(pair.right());
                }
                """);

        // When & Then
        assertThat(assembly).contains("bl juno_alloc");
    }

    @Test
    @DisplayName("Keeps an object passed to a method that is not inlined")
    void shouldKeepAnObjectPassedToAMethodThatIsNotInlined() throws Exception {
        // Given
        String assembly = compile("""
                static final class Holder { int value; }
                static void fill(Holder holder) {
                    holder.value = Random.nextInt(1024);
                    Delay.millis(1);
                }
                public static void main(String[] args) {
                    Holder holder = new Holder();
                    fill(holder);
                    Delay.millis(holder.value);
                }
                """);

        // When & Then
        assertThat(assembly).contains("bl juno_alloc", "bl juno_fn");
    }

    private String compile(String members) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                import io.github.jabrena.juno.util.Random;
                public final class Inlining {
                %s
                }
                """.formatted(members.indent(4));
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Inlining", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Inlining").assembly();
    }
}
