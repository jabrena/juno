package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;

import java.util.Set;

/**
 * The one place a method may return {@code java.lang.Object}: a board-side JDK class, one of {@code juno-api}'s
 * {@code io.github.jabrena.juno.jdk} classes, which the class reader names {@code java.*} (no real JDK class is ever
 * linked). That is the erased result of a generic getter such as {@code HttpResponse<T>.body()}, and of the bridge
 * method javac writes beside it. {@code checkcast} generates no code, so the caller relies on the value being the type
 * it casts to: {@code juno-api} guarantees that, a program's own method could not, so a program's {@code Object}
 * result stays rejected.
 */
final class BoardJdkErasedReturn {
    /** The package prefix the class reader gives {@code juno-api}'s board-side JDK classes. */
    private static final String BOARD_JDK_PREFIX = "java/";
    private static final String OBJECT_TYPE = "Ljava/lang/Object;";

    private BoardJdkErasedReturn() {
    }

    /** Whether {@code owner} is a board-side JDK class returning {@code Object}, with only supported parameters. */
    static boolean accepts(JavaClass owner, Descriptor descriptor, Set<String> referenceClassNames) {
        return owner.name().startsWith(BOARD_JDK_PREFIX) && descriptor.returnType().equals(OBJECT_TYPE)
                && new Descriptor(descriptor.parameters(), "V").usesOnlyV01Types(referenceClassNames);
    }
}
