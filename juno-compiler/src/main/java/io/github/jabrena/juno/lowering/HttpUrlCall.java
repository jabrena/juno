package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lowers an {@code HttpClient} call made with a URL. The URL is a compile-time literal, so its scheme picks the plain
 * or the TLS request here, at compile time, and the call becomes the host/port/path request the runtime already has:
 * no URL is parsed on the board.
 */
final class HttpUrlCall {
    private record Route(Intrinsic plain, Intrinsic secure) {
        Intrinsic of(HttpUrl url) {
            return url.secure() ? secure : plain;
        }
    }

    private static final Map<Intrinsic, Route> ROUTES = routes();

    private final MethodContext context;

    HttpUrlCall(MethodContext context) {
        this.context = context;
    }

    /** Whether {@code intrinsic} is a URL-based call this class lowers. */
    static boolean handles(Intrinsic intrinsic) {
        return ROUTES.containsKey(intrinsic);
    }

    /**
     * Emits the request {@code arguments} describe. Its first literal is the URL; the rest of its literals (a request
     * body) and its runtime values (buffers, lengths and, for {@link Intrinsic#HTTP_URL_GET_PATH_BUFFER}, the path
     * buffer) follow the host, port and path in the order the request's shim takes them.
     */
    void emit(Instruction instruction, MethodRef called, Intrinsic intrinsic, CallArguments arguments,
              Optional<Value> result) {
        String at = context.at(instruction) + ": " + called.displayName();
        HttpUrl url = parse(at, arguments.literals().get(0));
        boolean runtimePath = intrinsic == Intrinsic.HTTP_URL_GET_PATH_BUFFER;
        if (runtimePath && !url.isBase()) {
            throw new CompileException(at + ": the base URL of a request with a runtime path must have no path of its"
                    + " own, but \"" + arguments.literals().get(0) + "\" has \"" + url.path() + "\"");
        }
        requireCapability(at, url);

        Value port = context.values().int32();
        context.emit(new IrInstruction.Const(port, url.port()));
        List<Value> values = new ArrayList<>();
        values.add(port);
        values.addAll(arguments.values());
        List<String> literals = new ArrayList<>();
        literals.add(url.host());
        if (!runtimePath) {
            literals.add(url.path());
        }
        literals.addAll(arguments.literals().subList(1, arguments.literals().size()));
        Intrinsic request = Objects.requireNonNull(ROUTES.get(intrinsic)).of(url);
        context.emit(new IrInstruction.IntrinsicCall(result, request, Optional.empty(), List.copyOf(values),
                List.copyOf(literals)));
    }

    private static HttpUrl parse(String at, String url) {
        try {
            return HttpUrl.parse(url);
        } catch (IllegalArgumentException invalid) {
            throw new CompileException(at + ": " + invalid.getMessage());
        }
    }

    /** The linker only knew a request needs Wi-Fi; the scheme now says whether it needs plain HTTP or TLS. */
    private void requireCapability(String at, HttpUrl url) {
        Capability capability = url.secure() ? Capability.HTTPS_CLIENT : Capability.HTTP_CLIENT;
        Board board = context.program().board();
        if (!board.supports(capability)) {
            throw new CompileException(at + ": " + (url.secure() ? "an https:// URL" : "an http:// URL") + " needs "
                    + capability.apiName() + ": " + capability.unsupportedReason(board));
        }
    }

    private static Map<Intrinsic, Route> routes() {
        Map<Intrinsic, Route> routes = new EnumMap<>(Intrinsic.class);
        routes.put(Intrinsic.HTTP_URL_GET, new Route(Intrinsic.HTTP_GET, Intrinsic.HTTPS_GET));
        routes.put(Intrinsic.HTTP_URL_GET_PATH_BUFFER,
                new Route(Intrinsic.HTTP_GET_PATH_BUFFER, Intrinsic.HTTPS_GET_PATH_BUFFER));
        routes.put(Intrinsic.HTTP_URL_POST, new Route(Intrinsic.HTTP_POST, Intrinsic.HTTPS_POST));
        routes.put(Intrinsic.HTTP_URL_DELETE, new Route(Intrinsic.HTTP_DELETE, Intrinsic.HTTPS_DELETE));
        routes.put(Intrinsic.HTTP_URL_PATCH, new Route(Intrinsic.HTTP_PATCH, Intrinsic.HTTPS_PATCH));
        routes.put(Intrinsic.HTTP_URL_QUERY, new Route(Intrinsic.HTTP_QUERY, Intrinsic.HTTPS_QUERY));
        return Map.copyOf(routes);
    }
}
