package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.function.Function;

import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESPECIAL;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC_W;

/**
 * Reads, from a program's reachable methods, which URI schemes its {@code URI.create(...)} and {@code new URI(...)}
 * calls name (see {@link HttpSchemes}). A call given a string constant — {@code ldc} just before it, which is also what
 * javac emits for a {@code static final} constant — names that constant's scheme; a call given anything else could name
 * either, so it counts as both. The board-side {@code URI} itself is skipped: {@code create} hands its own argument to
 * the constructor, and that call names no scheme of the program's.
 */
final class UriSchemeScan {
    private static final String URI_CLASS = "java/net/URI";
    private static final MethodRef URI_CREATE = new MethodRef(URI_CLASS, "create",
            "(Ljava/lang/String;)Ljava/net/URI;");
    private static final MethodRef URI_CONSTRUCTOR = new MethodRef(URI_CLASS, "<init>", "(Ljava/lang/String;)V");

    /**
     * Links the program with {@code pass}, once or twice. The first pass assumes {@link HttpSchemes#NONE}, so neither
     * HTTP request is linked while the program's URIs are read from what is reachable — without the unused request's
     * shim, or a capability it needs, taking part. Only a program that asks a scheme query is linked again, with the
     * schemes its {@code URI.create} calls name; any other program keeps its first, and only, pass.
     */
    ReachabilityClosure.Result link(Function<HttpSchemes, ReachabilityClosure.Result> pass) {
        ReachabilityClosure.Result first = pass.apply(HttpSchemes.NONE);
        if (!asksSchemeQuery(first.methods())) {
            return first;
        }
        return pass.apply(schemesOf(first.methods()));
    }

    /** Whether any of {@code methods} asks a scheme query, so that the answer to it matters. */
    boolean asksSchemeQuery(List<LinkedMethod> methods) {
        for (LinkedMethod method : methods) {
            ConstantPool pool = method.owner().constantPool();
            for (Instruction instruction : method.instructions()) {
                if (instruction.opcode() == INVOKESTATIC) {
                    MethodRef called = pool.methodRef(instruction.operandA());
                    if (called.equals(HttpSchemeFolding.HTTP_QUERY) || called.equals(HttpSchemeFolding.HTTPS_QUERY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The schemes every {@code URI.create} and {@code new URI} call in {@code methods} names together. */
    HttpSchemes schemesOf(List<LinkedMethod> methods) {
        HttpSchemes schemes = HttpSchemes.NONE;
        for (LinkedMethod method : methods) {
            if (method.owner().name().equals(URI_CLASS)) {
                continue;
            }
            ConstantPool pool = method.owner().constantPool();
            List<Instruction> instructions = method.instructions();
            for (int index = 0; index < instructions.size(); index++) {
                if (!createsUri(instructions.get(index), pool)) {
                    continue;
                }
                schemes = schemes.with(index > 0 ? schemeOf(instructions.get(index - 1), pool) : HttpSchemes.BOTH);
            }
        }
        return schemes;
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
    private HttpSchemes schemeOf(Instruction loaded, ConstantPool pool) {
        boolean constant = (loaded.opcode() == LDC || loaded.opcode() == LDC_W) && pool.isString(loaded.operandA());
        if (!constant) {
            return HttpSchemes.BOTH;
        }
        String uri = pool.string(loaded.operandA());
        if (uri.startsWith("https://")) {
            return new HttpSchemes(false, true);
        }
        if (uri.startsWith("http://")) {
            return new HttpSchemes(true, false);
        }
        // Any other scheme is rejected by URI.create when it runs, so it sends no request.
        return HttpSchemes.NONE;
    }
}
