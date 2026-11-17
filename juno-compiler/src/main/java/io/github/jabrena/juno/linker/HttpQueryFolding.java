package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.jabrena.juno.bytecode.Opcodes.GOTO;
import static io.github.jabrena.juno.bytecode.Opcodes.IFEQ;
import static io.github.jabrena.juno.bytecode.Opcodes.IFNE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.POP;

/**
 * Decides a branch on one of the {@code java.net.http} link-time queries against what the program does (see
 * {@link HttpLinkFacts}): {@code NativeHttpSchemes.http()}/{@code https()}, so the request code of a scheme the program
 * never sends to — and the shim behind it — is gone, and the {@code NativeHttpFeatures} queries, so a program that
 * never reads headers, follows redirects, sets its own method or a timeout keeps a small header buffer, and links no
 * redirect, {@code CONNECT} or timeout code. The rewrite happens before reachability looks at the method, and is the one
 * {@link BoardQueryFolding} makes: the query stays, its {@code ifeq}/{@code ifne} becomes a {@code pop} plus a
 * {@code goto} when the jump is always taken, and no instruction moves, so every other branch keeps its target.
 * Anything else is left exactly as it was.
 */
final class HttpQueryFolding {
    private static final String SCHEMES = "io/github/jabrena/juno/hardware/internal/NativeHttpSchemes";
    private static final String FEATURES = "io/github/jabrena/juno/hardware/internal/NativeHttpFeatures";
    /** Each query, and the fact that answers it. */
    private static final Map<MethodRef, HttpLinkFacts.Fact> QUERIES = Map.of(
            new MethodRef(SCHEMES, "http", "()Z"), HttpLinkFacts.Fact.HTTP,
            new MethodRef(SCHEMES, "https", "()Z"), HttpLinkFacts.Fact.HTTPS,
            new MethodRef(FEATURES, "responseHeaders", "()Z"), HttpLinkFacts.Fact.RESPONSE_HEADERS,
            new MethodRef(FEATURES, "redirects", "()Z"), HttpLinkFacts.Fact.REDIRECTS,
            new MethodRef(FEATURES, "customMethods", "()Z"), HttpLinkFacts.Fact.CUSTOM_METHODS,
            new MethodRef(FEATURES, "timeouts", "()Z"), HttpLinkFacts.Fact.TIMEOUTS,
            new MethodRef(FEATURES, "customTrust", "()Z"), HttpLinkFacts.Fact.CUSTOM_TRUST);

    /**
     * Folds the queries left in {@code boardFolded}, reporting {@code changed} when either folding decided a branch, so
     * the caller prunes once for both.
     */
    BoardQueryFolding.Folded foldAfter(BoardQueryFolding.Folded boardFolded, JavaClass owner, HttpLinkFacts facts) {
        BoardQueryFolding.Folded folded = fold(boardFolded.instructions(), owner, facts);
        return new BoardQueryFolding.Folded(folded.instructions(), boardFolded.changed() || folded.changed());
    }

    /** Rewrites every branch on a query in {@code instructions}, reporting {@code changed} only when one was. */
    BoardQueryFolding.Folded fold(List<Instruction> instructions, JavaClass owner, HttpLinkFacts facts) {
        List<Instruction> rewritten = new ArrayList<>(instructions.size() + 2);
        boolean changed = false;
        int index = 0;
        while (index < instructions.size()) {
            Instruction instruction = instructions.get(index);
            rewritten.add(instruction);
            index++;
            Boolean answer = answer(instruction, owner, facts);
            if (answer == null || index >= instructions.size()) {
                continue;
            }
            Instruction branch = instructions.get(index);
            if (branch.opcode() != IFEQ && branch.opcode() != IFNE) {
                continue;
            }
            boolean taken = branch.opcode() == IFNE ? answer : !answer;
            int fallthrough = index + 1 < instructions.size() ? instructions.get(index + 1).offset() : -1;
            int target = taken ? branch.offset() + branch.operandA() : fallthrough;
            rewritten.add(new Instruction(branch.offset(), POP, 0, 0));
            if (target != fallthrough) {
                // One byte along, inside the bytes the branch occupied, as BoardQueryFolding places its goto.
                rewritten.add(new Instruction(branch.offset() + 1, GOTO, target - (branch.offset() + 1), 0));
            }
            index++;
            changed = true;
        }
        return new BoardQueryFolding.Folded(changed ? List.copyOf(rewritten) : instructions, changed);
    }

    /** Whether {@code called} is one of the link-time queries. */
    static boolean isQuery(MethodRef called) {
        return QUERIES.containsKey(called);
    }

    /** Whether {@code instruction} invokes a query, as the program's answer to it; otherwise {@code null}. */
    private @Nullable Boolean answer(Instruction instruction, JavaClass owner, HttpLinkFacts facts) {
        if (instruction.opcode() != INVOKESTATIC) {
            return null;
        }
        HttpLinkFacts.Fact fact = QUERIES.get(owner.constantPool().methodRef(instruction.operandA()));
        return fact == null ? null : facts.has(fact);
    }
}
