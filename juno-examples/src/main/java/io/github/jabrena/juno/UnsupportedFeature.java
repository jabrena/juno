package io.github.jabrena.juno;

/**
 * Deliberately outside Juno's supported subset: string concatenation with {@code +} compiles to
 * {@code invokedynamic} using {@code StringConcatFactory}, which Juno does not lower. Juno accepts
 * {@code invokedynamic} only for lambdas and method references bootstrapped by
 * {@code LambdaMetafactory.metafactory}. {@code javac} accepts this class like any other; only
 * {@code juno:compile} rejects it with a diagnostic naming the method and bytecode offset.
 */
public final class UnsupportedFeature {
    private UnsupportedFeature() {
    }

    public static void main(String[] args) {
        int temperature = 5;
        String message = "temperature=" + temperature;
    }
}
