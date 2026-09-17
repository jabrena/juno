package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Set;

public final class Intrinsics {
    private static final Set<MethodRef> METHODS = Set.of(
            new MethodRef("io/github/jabrena/juno/api/Gpio", "pinMode", "(II)V"),
            new MethodRef("io/github/jabrena/juno/api/Gpio", "digitalWrite", "(IZ)V"),
            new MethodRef("io/github/jabrena/juno/api/Gpio", "digitalRead", "(I)Z"),
            new MethodRef("io/github/jabrena/juno/api/Gpio", "analogRead", "(I)I"),
            new MethodRef("io/github/jabrena/juno/api/Gpio", "analogWrite", "(II)V"),
            new MethodRef("io/github/jabrena/juno/api/Gpio", "toggle", "(I)V"),
            new MethodRef("io/github/jabrena/juno/api/Delay", "millis", "(I)V"),
            new MethodRef("io/github/jabrena/juno/api/Delay", "micros", "(I)V"),
            new MethodRef("io/github/jabrena/juno/api/Clock", "millis", "()I"),
            new MethodRef("io/github/jabrena/juno/api/Clock", "micros", "()I"),
            new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "of",
                    "(I)Lio/github/jabrena/juno/api/DigitalOutput;"),
            new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "high", "()V"),
            new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "low", "()V"),
            new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "toggle", "()V"),
            new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "isHigh", "()Z"));

    private Intrinsics() {
    }

    public static boolean contains(MethodRef method) {
        return METHODS.contains(method);
    }
}
