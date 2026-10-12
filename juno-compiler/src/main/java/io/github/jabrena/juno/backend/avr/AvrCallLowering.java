package io.github.jabrena.juno.backend.avr;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.ProgramLayout;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Calls between generated AVR functions: the argument words go to {@code juno_avr_args} (a lambda's captured values
 * read from its object on the way), the callee copies the ones it reads into its frame, and the result comes back in
 * r22-r25 or r18-r25. An interface call compares the receiver's type id against each reachable implementation.
 */
final class AvrCallLowering {
    private static final int A = AvrEmitter.A;
    private static final int B = AvrEmitter.B;

    private final AvrEmitter avr;
    private final ProgramLayout layout;
    private final AvrMemoryLowering memory;
    private final Map<MethodRef, String> functionLabels;

    AvrCallLowering(AvrEmitter avr, ProgramLayout layout, AvrMemoryLowering memory,
                    Map<MethodRef, String> functionLabels) {
        this.avr = avr;
        this.layout = layout;
        this.memory = memory;
        this.functionLabels = functionLabels;
    }

    /** One argument word of a call: a plain source, or a lambda's captured value read from its object. */
    private sealed interface ArgumentWord {
        record Plain(WordSource source) implements ArgumentWord {
        }

        record Capture(Value lambda, int index) implements ArgumentWord {
        }
    }

    private void storeArgumentWords(StringBuilder output, FrameLayout frame, List<ArgumentWord> words) {
        for (int index = 0; index < words.size(); index++) {
            switch (words.get(index)) {
                case ArgumentWord.Plain plain -> avr.loadWord(output, frame, plain.source(), A);
                case ArgumentWord.Capture capture -> {
                    avr.loadPointer(output, frame, capture.lambda());
                    AvrEmitter.loadFromZ(output, A, (capture.index() + 1) * AvrEmitter.WORD, AvrEmitter.WORD);
                }
            }
            avr.storeArgument(output, A, index);
        }
    }

    void emitResolvedCall(StringBuilder output, FrameLayout frame, MethodRef method, List<WordSource> words,
                                  Optional<Value> target) {
        String label = functionLabels.get(method);
        if (label == null) {
            throw AvrAsmBackend.unsupported("call to unresolved method " + method.displayName());
        }
        storeArgumentWords(output, frame, words.stream().map(ArgumentWord.Plain::new)
                .map(ArgumentWord.class::cast).toList());
        output.append("    call ").append(label).append('\n');
        target.ifPresent(value -> storeReturn(output, frame, value));
    }

    private void storeReturn(StringBuilder output, FrameLayout frame, Value value) {
        if (FrameLayout.isWide(value.type())) {
            avr.store64(output, frame, B, A, value);
        } else {
            avr.store(output, frame, A, value);
        }
    }

    void emitLambdaCreate(StringBuilder output, FrameLayout frame, IrInstruction.LambdaCreate lambda) {
        if (!lambda.site().isCapturing()) {
            avr.loadAddress(output, A, layout.lambdaFunctionSymbol(lambda.site()));
            avr.store(output, frame, A, lambda.target());
            return;
        }
        memory.emitAllocate(output, layout.objectSize(lambda.site().syntheticClassName()));
        avr.store(output, frame, A, lambda.target());
        avr.loadImmediate(output, B,
                Objects.requireNonNullElse(layout.objectTypeId(lambda.site().syntheticClassName()), 0));
        output.append("    movw r30, r22\n");
        AvrEmitter.storeToZ(output, B, 0, AvrEmitter.WORD);
        for (int index = 0; index < lambda.captures().size(); index++) {
            final int capture = index;
            memory.storeValueToZ(output, frame, lambda.captures().get(index),
                    () -> avr.loadPointer(output, frame, lambda.target()), (capture + 1) * AvrEmitter.WORD);
        }
    }

    void emitLambdaCall(StringBuilder output, FrameLayout frame, IrInstruction.LambdaCall call) {
        MethodHandleRef implementation = call.site().implementation();
        boolean constructor = implementation.referenceKind() == MethodHandleRef.REF_NEW_INVOKE_SPECIAL;
        List<ArgumentWord> words = new ArrayList<>();
        if (constructor) {
            Value result = call.target().orElseThrow(() -> AvrAsmBackend.unsupported("constructor reference without a result"));
            memory.emitNewObject(output, frame, implementation.method().owner(), result);
            words.add(new ArgumentWord.Plain(new WordSource.FromValue(result)));
        }
        Value lambdaReference = call.arguments().getFirst();
        int captureCount = call.site().captureTypes().size();
        for (int index = 0; index < captureCount; index++) {
            words.add(new ArgumentWord.Capture(lambdaReference, index));
        }
        for (Value argument : call.arguments().subList(1, call.arguments().size())) {
            if (FrameLayout.isWide(argument.type())) {
                words.add(new ArgumentWord.Plain(new WordSource.FromValueLow(argument)));
                words.add(new ArgumentWord.Plain(new WordSource.FromValueHigh(argument)));
            } else {
                words.add(new ArgumentWord.Plain(new WordSource.FromValue(argument)));
            }
        }
        String label = functionLabels.get(implementation.method());
        if (label == null) {
            throw AvrAsmBackend.unsupported("lambda call to unresolved method " + implementation.method().displayName());
        }
        storeArgumentWords(output, frame, words);
        output.append("    call ").append(label).append('\n');
        if (!constructor) {
            call.target().ifPresent(value -> storeReturn(output, frame, value));
        }
    }

    void emitInterfaceCall(StringBuilder output, FrameLayout frame, IrInstruction.InterfaceCall call) {
        String done = avr.newLabel(".LinterfaceDone");
        for (var target : call.targets()) {
            String next = avr.newLabel(".LinterfaceNext");
            // Reloaded per target: the previous candidate's compare only ever falls through here.
            avr.loadPointer(output, frame, call.arguments().getFirst());
            AvrEmitter.loadFromZ(output, A, 0, AvrEmitter.WORD);
            avr.loadImmediate(output, B, target.typeId());
            AvrEmitter.compareQuads(output, A, B);
            output.append("    breq 1f\n")
                    .append("    jmp ").append(next).append('\n')
                    .append("1:\n");
            if (target.isLambda()) {
                emitLambdaCall(output, frame, new IrInstruction.LambdaCall(call.target(),
                        Objects.requireNonNull(target.lambda()), call.arguments()));
            } else {
                emitResolvedCall(output, frame, target.method(), WordSource.argumentWords(call.arguments()),
                        call.target());
            }
            output.append("    jmp ").append(done).append('\n')
                    .append(next).append(":\n");
        }
        output.append("    call juno_panic\n")
                .append(done).append(":\n");
    }
}
