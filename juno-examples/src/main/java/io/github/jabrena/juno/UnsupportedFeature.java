package io.github.jabrena.juno;

/**
 * Deliberately outside Juno's supported subset: string concatenation with {@code +} compiles to
 * {@code invokedynamic} (opcode 0xba), which Juno does not lower. {@code javac} accepts this class
 * like any other; only {@code juno:compile} rejects it, with a diagnostic naming the method,
 * bytecode offset, and opcode. See the README's Supported Java subset section.
 */
public final class UnsupportedFeature {
    private UnsupportedFeature() {
    }

    public static void main(String[] args) {
        int temperature = 5;
        String message = "temperature=" + temperature;
    }
}
