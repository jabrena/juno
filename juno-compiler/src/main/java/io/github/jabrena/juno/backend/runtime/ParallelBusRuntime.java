package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.core.CoreRuntime;

/**
 * The shim half of {@code ParallelBus}. Each pin is resolved once to its GPIO port and bit; a byte then becomes one
 * set/clear write per port the data pins span, and the strobe two more. The bit-to-port bookkeeping is the same on
 * every core: a {@link CoreRuntime} only supplies its port handle type, how an Arduino pin number resolves to a port
 * and bit mask, and the write that sets some bits of a port and clears others in one go.
 */
public final class ParallelBusRuntime {
    private ParallelBusRuntime() {
    }

    /**
     * @param prelude   includes and declarations the core's snippets need
     * @param portType  the C type of a port handle
     * @param resolve   statements setting {@code port} and {@code mask} from the Arduino pin number {@code pin}
     * @param write     a statement setting bits {@code set} and clearing bits {@code clear} of {@code port}
     */
    public static String helpers(String prelude, String portType, String resolve, String write) {
        return RuntimeSource.load("common/parallel-bus.cpp")
                .replace("${PRELUDE}", prelude)
                .replace("${PORT_TYPE}", portType)
                .replace("${RESOLVE}", resolve)
                .replace("${WRITE}", write);
    }
}
