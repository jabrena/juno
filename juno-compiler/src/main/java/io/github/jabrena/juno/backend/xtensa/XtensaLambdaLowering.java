package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.ProgramLayout;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.B;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.WORD;

/**
 * Lambdas and method references, with the representation every target shares (see {@link ProgramLayout}): a
 * non-capturing lambda is the address of a read-only word holding its type id, and a capturing one an arena object of
 * that type id followed by one word per capture. A call goes straight to the implementation method, its argument
 * words in {@code a10}-{@code a15} in the implementation's parameter order: a constructor reference's new object,
 * then the captures, then the call's own arguments, a {@code long}/{@code double} starting at an even word.
 */
final class XtensaLambdaLowering {
    private final XtensaEmitter xtensa;
    private final ProgramLayout layout;
    private final XtensaMemoryLowering memory;
    private final Map<MethodRef, String> functionLabels;

    XtensaLambdaLowering(XtensaEmitter xtensa, ProgramLayout layout, XtensaMemoryLowering memory,
                         Map<MethodRef, String> functionLabels) {
        this.xtensa = xtensa;
        this.layout = layout;
        this.memory = memory;
        this.functionLabels = functionLabels;
    }

    void emitCreate(StringBuilder output, FrameLayout frame, IrInstruction.LambdaCreate lambda) {
        if (!lambda.site().isCapturing()) {
            xtensa.loadAddress(output, A, layout.lambdaFunctionSymbol(lambda.site()));
            xtensa.store(output, frame, A, lambda.target());
            return;
        }
        memory.emitNewObject(output, frame, lambda.site().syntheticClassName(), lambda.target());
        for (int index = 0; index < lambda.captures().size(); index++) {
            xtensa.load(output, frame, A, lambda.target());
            xtensa.load(output, frame, B, lambda.captures().get(index));
            xtensa.storeWordAt(output, B, A, (index + 1) * WORD);
        }
    }

    void emitCall(StringBuilder output, FrameLayout frame, IrInstruction.LambdaCall call) {
        MethodHandleRef implementation = call.site().implementation();
        boolean constructor = implementation.referenceKind() == MethodHandleRef.REF_NEW_INVOKE_SPECIAL;
        List<ArgumentWord> words = new ArrayList<>();
        if (constructor) {
            Value result = call.target().orElseThrow(
                    () -> Architecture.XTENSA.unsupported("constructor reference without a result"));
            memory.emitNewObject(output, frame, implementation.method().owner(), result);
            words.add(new ArgumentWord.Plain(new WordSource.FromValue(result)));
        }
        Value lambdaReference = call.arguments().getFirst();
        for (int index = 0; index < call.site().captureTypes().size(); index++) {
            words.add(new ArgumentWord.Capture(lambdaReference, index));
        }
        for (Value argument : call.arguments().subList(1, call.arguments().size())) {
            if (argument.type().isWide()) {
                if (words.size() % 2 != 0) {
                    words.add(new ArgumentWord.Plain(new WordSource.Immediate(0)));
                }
                words.add(new ArgumentWord.Plain(new WordSource.FromValueLow(argument)));
                words.add(new ArgumentWord.Plain(new WordSource.FromValueHigh(argument)));
            } else {
                words.add(new ArgumentWord.Plain(new WordSource.FromValue(argument)));
            }
        }
        String label = functionLabels.get(implementation.method());
        if (label == null) {
            throw Architecture.XTENSA.unsupported("lambda call to unresolved method "
                    + implementation.method().displayName());
        }
        if (words.size() > XtensaEmitter.REGISTER_ARGUMENT_WORDS) {
            throw Architecture.XTENSA.unsupported("a lambda call with more than "
                    + XtensaEmitter.REGISTER_ARGUMENT_WORDS + " argument words (" + implementation.method().displayName()
                    + ")");
        }
        for (int index = 0; index < words.size(); index++) {
            words.get(index).load(output, frame, xtensa, XtensaEmitter.argumentRegister(index));
        }
        xtensa.call(output, label);
        if (!constructor) {
            call.target().ifPresent(target -> {
                if (target.type().isWide()) {
                    xtensa.store64(output, frame, "a10", "a11", target);
                } else {
                    xtensa.store(output, frame, "a10", target);
                }
            });
        }
    }

    /** One argument word of a lambda call: an ordinary word, or a capture read out of the lambda object. */
    private sealed interface ArgumentWord {
        void load(StringBuilder output, FrameLayout frame, XtensaEmitter xtensa, String register);

        record Plain(WordSource word) implements ArgumentWord {
            @Override
            public void load(StringBuilder output, FrameLayout frame, XtensaEmitter xtensa, String register) {
                xtensa.loadWord(output, frame, word, register);
            }
        }

        record Capture(Value lambda, int index) implements ArgumentWord {
            @Override
            public void load(StringBuilder output, FrameLayout frame, XtensaEmitter xtensa, String register) {
                xtensa.load(output, frame, A, lambda);
                xtensa.loadWordAt(output, register, A, (index + 1) * WORD);
            }
        }
    }
}
