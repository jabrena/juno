package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.github.jabrena.juno.bytecode.Opcodes.GOTO;
import static io.github.jabrena.juno.bytecode.Opcodes.IFEQ;
import static io.github.jabrena.juno.bytecode.Opcodes.IFNE;
import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.POP;

/**
 * Decides a branch on {@code NativeHttpSchemes.http()} or {@code https()} against the schemes the program uses (see
 * {@link HttpSchemes}), so the request code of a scheme the program never sends to — and the shim behind it — is gone
 * before reachability looks at the method. It is the same rewrite {@link BoardQueryFolding} makes: the query stays,
 * its {@code ifeq}/{@code ifne} becomes a {@code pop} plus a {@code goto} when the jump is always taken, and no
 * instruction moves, so every other branch keeps its target. Anything else is left exactly as it was.
 */
final class HttpSchemeFolding {
    static final MethodRef HTTP_QUERY = new MethodRef("io/github/jabrena/juno/hardware/internal/NativeHttpSchemes",
            "http", "()Z");
    static final MethodRef HTTPS_QUERY = new MethodRef("io/github/jabrena/juno/hardware/internal/NativeHttpSchemes",
            "https", "()Z");

    /**
     * Folds the scheme queries left in {@code boardFolded}, reporting {@code changed} when either folding decided a
     * branch, so the caller prunes once for both.
     */
    BoardQueryFolding.Folded foldAfter(BoardQueryFolding.Folded boardFolded, JavaClass owner, HttpSchemes schemes) {
        BoardQueryFolding.Folded folded = fold(boardFolded.instructions(), owner, schemes);
        return new BoardQueryFolding.Folded(folded.instructions(), boardFolded.changed() || folded.changed());
    }

    /** Rewrites every branch on a scheme query in {@code instructions}, reporting {@code changed} only when one was. */
    BoardQueryFolding.Folded fold(List<Instruction> instructions, JavaClass owner, HttpSchemes schemes) {
        List<Instruction> rewritten = new ArrayList<>(instructions.size() + 2);
        boolean changed = false;
        int index = 0;
        while (index < instructions.size()) {
            Instruction instruction = instructions.get(index);
            rewritten.add(instruction);
            index++;
            Boolean answer = answer(instruction, owner, schemes);
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

    /** Whether {@code instruction} invokes a scheme query, as the program's answer to it; otherwise {@code null}. */
    private @Nullable Boolean answer(Instruction instruction, JavaClass owner, HttpSchemes schemes) {
        if (instruction.opcode() != INVOKESTATIC) {
            return null;
        }
        MethodRef called = owner.constantPool().methodRef(instruction.operandA());
        if (called.equals(HTTP_QUERY)) {
            return schemes.http();
        }
        if (called.equals(HTTPS_QUERY)) {
            return schemes.https();
        }
        return null;
    }
}
