package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.CompilerTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Which calls the inliner removes and which allocations scalar replacement removes, compiled from real Java. */
class InliningAndScalarReplacementTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void inlinesASmallLeafHelperWithABranch() throws Exception {
        String assembly = compile("""
                static int clamp(int value) { return value < 0 ? 0 : value > 99 ? 99 : value; }
                public static void main(String[] args) { Delay.millis(clamp(Gpio.analogRead(0))); }
                """);
        assertThat(assembly).doesNotContain("bl juno_fn");
    }

    @Test
    void keepsACallToAMethodThatCallsSomethingElse() throws Exception {
        String assembly = compile("""
                static void blink(int pin) { Gpio.digitalWrite(pin, true); Delay.millis(5); }
                public static void main(String[] args) { blink(13); blink(12); }
                """);
        assertThat(assembly).contains("bl juno_fn");
    }

    @Test
    void keepsACallToAMethodTooLargeToInline() throws Exception {
        String assembly = compile("""
                static int mix(int a, int b) {
                    return (a * 3 + b) ^ (a >> 2) ^ (b << 5) ^ (a - b) ^ (a | b) ^ (a & 7) ^ (b % 11);
                }
                public static void main(String[] args) { Delay.millis(mix(Gpio.analogRead(0), Gpio.analogRead(1))); }
                """);
        assertThat(assembly).contains("bl juno_fn");
    }

    @Test
    void keepsACallWithALongParameter() throws Exception {
        String assembly = compile("""
                static int low(long value) { return (int) value; }
                public static void main(String[] args) { Delay.millis(low(Gpio.analogRead(0))); }
                """);
        assertThat(assembly).contains("bl juno_fn");
    }

    @Test
    void replacesARecordThatNeverLeavesTheMethod() throws Exception {
        String assembly = compile("""
                record Pair(int left, int right) {}
                public static void main(String[] args) {
                    int total = 0;
                    for (int i = 0; i < 10; i++) {
                        Pair pair = new Pair(i, Gpio.analogRead(0));
                        total += pair.left() * pair.right();
                    }
                    Delay.millis(total);
                }
                """);
        assertThat(assembly).doesNotContain("bl juno_alloc", "bl juno_fn");
    }

    @Test
    void keepsAnObjectStoredInAStaticField() throws Exception {
        String assembly = compile("""
                record Pair(int left, int right) {}
                static Pair last;
                public static void main(String[] args) {
                    Pair pair = new Pair(1, Gpio.analogRead(0));
                    last = pair;
                    Delay.millis(pair.right());
                }
                """);
        assertThat(assembly).contains("bl juno_alloc");
    }

    @Test
    void keepsAnObjectPassedToAMethodThatIsNotInlined() throws Exception {
        String assembly = compile("""
                static final class Holder { int value; }
                static void fill(Holder holder) {
                    holder.value = Gpio.analogRead(0);
                    Delay.millis(1);
                }
                public static void main(String[] args) {
                    Holder holder = new Holder();
                    fill(holder);
                    Delay.millis(holder.value);
                }
                """);
        assertThat(assembly).contains("bl juno_alloc", "bl juno_fn");
    }

    private String compile(String members) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class Inlining {
                %s
                }
                """.formatted(members.indent(4));
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Inlining", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Inlining").assembly();
    }
}
