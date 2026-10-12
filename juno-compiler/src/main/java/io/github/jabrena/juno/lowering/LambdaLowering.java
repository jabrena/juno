package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LambdaSite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Materializes lambdas and method references, and adapts their interface calls to implementation calls. */
final class LambdaLowering {
    private final MethodContext context;
    private final OperandStack stack;
    private final CallLowering calls;

    LambdaLowering(MethodContext context, CallLowering calls) {
        this.context = context;
        this.stack = context.stack();
        this.calls = calls;
    }

    /** The {@code invokedynamic} that creates the lambda: pops its captured values, last first. */
    void create(LambdaSite site) {
        Value[] captures = new Value[site.captureTypes().size()];
        for (int index = captures.length - 1; index >= 0; index--) {
            captures[index] = stack.pop();
        }
        Value lambda = context.values().int32();
        context.emit(new IrInstruction.LambdaCreate(lambda, site, List.of(captures)));
        stack.push(lambda);
    }

    /** An interface call whose only target is {@code site}'s lambda. */
    void call(Instruction instruction, LambdaSite site) {
        Descriptor descriptor = Descriptor.parse(site.interfaceMethod().descriptor());
        CallArguments arguments = calls.popArguments(instruction, site.interfaceMethod(), descriptor,
                Optional.empty());
        Value receiver = stack.pop();
        Optional<Value> result = Optional.ofNullable(calls.result(descriptor));
        List<Value> callArguments = new ArrayList<>();
        callArguments.add(receiver);
        callArguments.addAll(arguments.values());
        context.emit(new IrInstruction.LambdaCall(result, site, callArguments));
        result.ifPresent(stack::push);
    }
}
