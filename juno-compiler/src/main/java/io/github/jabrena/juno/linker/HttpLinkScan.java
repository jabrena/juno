package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.linker.HttpLinkFacts.Fact;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.github.jabrena.juno.bytecode.Opcodes.INVOKEINTERFACE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESPECIAL;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC_W;

/**
 * Reads, from a program's reachable methods, what it does with {@code java.net.http} (see {@link HttpLinkFacts}).
 *
 * <ul>
 *   <li>The schemes its {@code URI.create(...)} and {@code new URI(...)} calls name. A call given a string constant —
 *       {@code ldc} just before it, which is also what javac emits for a {@code static final} constant — names that
 *       constant's scheme; a call given anything else could name either, so it counts as both. The board-side
 *       {@code URI} itself is skipped: {@code create} hands its own argument to the constructor.</li>
 *   <li>Whether it reads a response's headers ({@code HttpResponse.headers()}), asks a client to follow redirects
 *       ({@code HttpClient.Builder.followRedirects(...)}), sets a method of its own
 *       ({@code HttpRequest.Builder.method(...)}), or a timeout ({@code HttpRequest.Builder.timeout(...)},
 *       {@code HttpClient.Builder.connectTimeout(...)}). Only the program's own calls count: the board-side
 *       {@code java.*} classes never decide this for it.</li>
 * </ul>
 */
final class HttpLinkScan {
    private static final String URI_CLASS = "java/net/URI";
    private static final String BOARD_JDK_PREFIX = "java/";
    private static final MethodRef URI_CREATE = new MethodRef(URI_CLASS, "create",
            "(Ljava/lang/String;)Ljava/net/URI;");
    private static final MethodRef URI_CONSTRUCTOR = new MethodRef(URI_CLASS, "<init>", "(Ljava/lang/String;)V");
    private static final String CLIENT_BUILDER = "java/net/http/HttpClient$Builder";
    private static final String REQUEST_BUILDER = "java/net/http/HttpRequest$Builder";
    /** The program calls that ask for optional client code, and what each asks for. */
    private static final Map<MethodRef, HttpLinkFacts> FEATURE_CALLS = Map.of(
            new MethodRef("java/net/http/HttpResponse", "headers", "()Ljava/net/http/HttpHeaders;"),
            HttpLinkFacts.of(Fact.RESPONSE_HEADERS),
            // Redirects read Location, so they need the response headers too.
            new MethodRef(CLIENT_BUILDER, "followRedirects",
                    "(Ljava/net/http/HttpClient$Redirect;)Ljava/net/http/HttpClient$Builder;"),
            HttpLinkFacts.of(Fact.RESPONSE_HEADERS, Fact.REDIRECTS),
            new MethodRef(REQUEST_BUILDER, "method",
                    "(Ljava/lang/String;Ljava/net/http/HttpRequest$BodyPublisher;)Ljava/net/http/HttpRequest$Builder;"),
            HttpLinkFacts.of(Fact.CUSTOM_METHODS),
            new MethodRef(REQUEST_BUILDER, "timeout", "(Ljava/time/Duration;)Ljava/net/http/HttpRequest$Builder;"),
            HttpLinkFacts.of(Fact.TIMEOUTS),
            new MethodRef(CLIENT_BUILDER, "connectTimeout", "(Ljava/time/Duration;)Ljava/net/http/HttpClient$Builder;"),
            HttpLinkFacts.of(Fact.TIMEOUTS));

    /**
     * Links the program with {@code pass}, once or twice. The first pass assumes {@link HttpLinkFacts#NONE}, so neither
     * HTTP request, nor a full header buffer, nor redirect code is linked while the program's own calls are read from
     * what is reachable — without an unused request's shim, or a capability it needs, taking part. Only a program that
     * asks a query is linked again, with what its calls show; any other program keeps its first, and only, pass.
     */
    ReachabilityClosure.Result link(Function<HttpLinkFacts, ReachabilityClosure.Result> pass) {
        ReachabilityClosure.Result first = pass.apply(HttpLinkFacts.NONE);
        if (!asksQuery(first.methods())) {
            return first;
        }
        return pass.apply(factsOf(first.methods()));
    }

    /** Whether any of {@code methods} asks a link-time query, so that the answer to it matters. */
    boolean asksQuery(List<LinkedMethod> methods) {
        for (LinkedMethod method : methods) {
            ConstantPool pool = method.owner().constantPool();
            for (Instruction instruction : method.instructions()) {
                if (instruction.opcode() == INVOKESTATIC
                        && HttpQueryFolding.isQuery(pool.methodRef(instruction.operandA()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** What every method in {@code methods} does with {@code java.net.http}, together. */
    HttpLinkFacts factsOf(List<LinkedMethod> methods) {
        HttpLinkFacts facts = HttpLinkFacts.NONE;
        for (LinkedMethod method : methods) {
            if (method.owner().name().startsWith(BOARD_JDK_PREFIX)) {
                continue;
            }
            ConstantPool pool = method.owner().constantPool();
            List<Instruction> instructions = method.instructions();
            for (int index = 0; index < instructions.size(); index++) {
                Instruction instruction = instructions.get(index);
                if (createsUri(instruction, pool)) {
                    facts = facts.with(index > 0 ? schemeOf(instructions.get(index - 1), pool)
                            : HttpLinkFacts.of(Fact.HTTP, Fact.HTTPS));
                } else if (instruction.opcode() == INVOKEINTERFACE) {
                    facts = facts.with(FEATURE_CALLS.getOrDefault(pool.methodRef(instruction.operandA()),
                            HttpLinkFacts.NONE));
                }
            }
        }
        return facts;
    }

    /** Whether {@code instruction} is a {@code URI.create(String)} or {@code new URI(String)} call. */
    private boolean createsUri(Instruction instruction, ConstantPool pool) {
        return switch (instruction.opcode()) {
            case INVOKESTATIC -> pool.methodRef(instruction.operandA()).equals(URI_CREATE);
            case INVOKESPECIAL -> pool.methodRef(instruction.operandA()).equals(URI_CONSTRUCTOR);
            default -> false;
        };
    }

    /** The scheme the argument {@code loaded} pushes names, or both when it is not a string constant. */
    private HttpLinkFacts schemeOf(Instruction loaded, ConstantPool pool) {
        boolean constant = (loaded.opcode() == LDC || loaded.opcode() == LDC_W) && pool.isString(loaded.operandA());
        if (!constant) {
            return HttpLinkFacts.of(Fact.HTTP, Fact.HTTPS);
        }
        String uri = pool.string(loaded.operandA());
        if (uri.startsWith("https://")) {
            return HttpLinkFacts.of(Fact.HTTPS);
        }
        if (uri.startsWith("http://")) {
            return HttpLinkFacts.of(Fact.HTTP);
        }
        // Any other scheme is rejected by URI.create when it runs, so it sends no request.
        return HttpLinkFacts.NONE;
    }
}
