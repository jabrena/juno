package io.github.jabrena.juno.backend.runtime;

/** Runtime implementation of the supported {@code java.util.UUID} operations. */
final class UuidRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    String helpers(int illegalArgumentClassId) {
        return runtimeSource.load("common/uuid.cpp")
                .replace("${JUNO_UUID_ILLEGAL_ARGUMENT_CLASS_ID}", Integer.toString(illegalArgumentClassId));
    }
}
