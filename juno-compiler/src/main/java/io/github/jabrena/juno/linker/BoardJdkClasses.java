package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The board-side JDK classes: {@code juno-api}'s {@code io.github.jabrena.juno.jdk} classes, which the class reader
 * names {@code java.*} (no real JDK class is ever linked). They are the only classes that may use
 * {@code java.lang.Object} in a method or a field: the erased {@code T} of a generic class such as
 * {@code HttpResponse<T>.body()}, {@code Optional<T>.get()} and {@code orElse(T)}, and the bridge methods javac writes
 * beside them. {@code checkcast} generates no code, so the caller relies on the value being the type it casts to:
 * {@code juno-api} guarantees that, a program's own class could not, so a program's {@code Object} stays rejected.
 */
public final class BoardJdkClasses {
    /** The package prefix the class reader gives {@code juno-api}'s board-side JDK classes. */
    private static final String BOARD_JDK_PREFIX = "java/";
    private static final String OBJECT_TYPE = "Ljava/lang/Object;";

    private BoardJdkClasses() {
    }

    /**
     * Whether {@code owner} is a board-side JDK class and {@code descriptor} uses only supported types, counting
     * {@code Object} as supported in any parameter and in the result.
     */
    static boolean acceptsMethod(JavaClass owner, Descriptor descriptor, Set<String> referenceClassNames) {
        if (!isBoardJdkClass(owner)) {
            return false;
        }
        List<String> parameters = new ArrayList<>();
        for (String parameter : descriptor.parameters()) {
            if (!parameter.equals(OBJECT_TYPE)) {
                parameters.add(parameter);
            }
        }
        String returnType = descriptor.returnType().equals(OBJECT_TYPE) ? "V" : descriptor.returnType();
        return new Descriptor(parameters, returnType).usesOnlyV01Types(referenceClassNames);
    }

    /** Whether a field of {@code owner} with {@code descriptor} is an {@code Object} field of a board-side JDK class. */
    public static boolean isObjectField(JavaClass owner, String descriptor) {
        return isBoardJdkClass(owner) && descriptor.equals(OBJECT_TYPE);
    }

    private static boolean isBoardJdkClass(JavaClass owner) {
        return owner.name().startsWith(BOARD_JDK_PREFIX);
    }
}
