package io.github.jabrena.juno.tny2.corpus;

/** An endless stream of training examples. */
@FunctionalInterface
public interface ExampleSource {
    Example next();
}
