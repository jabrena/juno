package io.github.jabrena.juno;

/**
 * Deliberately outside Juno's supported subset: a record's generated {@code toString()} uses the
 * {@code ObjectMethods} invokedynamic bootstrap. Juno accepts {@code invokedynamic} for lambdas,
 * method references, and bounded string concatenation, but does not lower generated record object
 * methods. {@code javac} accepts this class; {@code juno:compile} rejects the reachable
 * {@code toString()} with a diagnostic naming the method and bytecode offset.
 */
public record UnsupportedFeature(int temperature) {

    public static void main(String[] args) {
        String message = new UnsupportedFeature(5).toString();
    }
}
