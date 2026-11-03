package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.linker.ThrowableTypes;

/** Runtime implementation of the {@code java.net} datagram subset, on top of the shared UDP transport. */
final class DatagramRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    String helpers(int ioClassId, int illegalArgumentClassId) {
        return runtimeSource.load("common/datagram.cpp")
                .replace("${JUNO_DATAGRAM_IO}", Integer.toString(ioClassId))
                .replace("${JUNO_DATAGRAM_ILLEGAL_ARGUMENT}", Integer.toString(illegalArgumentClassId))
                .replace("${JUNO_THROWABLE_BYTES}", Integer.toString(ThrowableTypes.HEADER_BYTES))
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / Integer.BYTES))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / Integer.BYTES));
    }
}
