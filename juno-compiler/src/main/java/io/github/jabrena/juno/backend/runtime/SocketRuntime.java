package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.linker.ThrowableTypes;

/** Runtime implementation of the bounded {@code java.net.Socket} subset, on top of the TCP connection. */
final class SocketRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    String helpers(int ioClassId, int illegalArgumentClassId) {
        return runtimeSource.load("common/socket.cpp")
                .replace("${JUNO_SOCKET_IO}", Integer.toString(ioClassId))
                .replace("${JUNO_SOCKET_ILLEGAL_ARGUMENT}", Integer.toString(illegalArgumentClassId))
                .replace("${JUNO_THROWABLE_BYTES}", Integer.toString(ThrowableTypes.HEADER_BYTES))
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / Integer.BYTES))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / Integer.BYTES));
    }
}
