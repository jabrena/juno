package io.github.jabrena.juno.lm.corpus;

/** An endless stream of training examples. */
@FunctionalInterface
public interface ExampleSource {
    Example next();
}
