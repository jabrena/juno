package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.linker.LambdaCallSite;
import io.github.jabrena.juno.linker.LambdaSite;

/** {@code invokedynamic}: a lambda or method reference the linker resolved, or else a string concatenation. */
final class InvokeDynamicLowering implements InstructionLowering {
    private final MethodContext context;
    private final LambdaLowering lambdas;
    private final StringConcatLowering concatenations;
    private final ObjectMethodsLowering objectMethods;

    InvokeDynamicLowering(MethodContext context, LambdaLowering lambdas, StringConcatLowering concatenations,
                          ObjectMethodsLowering objectMethods) {
        this.context = context;
        this.lambdas = lambdas;
        this.concatenations = concatenations;
        this.objectMethods = objectMethods;
    }

    @Override
    public void lower(Instruction instruction) {
        LambdaCallSite callSite = new LambdaCallSite(context.method().method().reference(), instruction.offset());
        LambdaSite lambda = context.program().lambdaSites().get(callSite);
        if (lambda != null) {
            lambdas.create(lambda);
        } else if (context.program().objectMethodsResolver().supports(context.method(), instruction)) {
            objectMethods.lower(instruction);
        } else {
            concatenations.concat(instruction,
                    context.program().stringConcatResolver().resolve(context.method(), instruction));
        }
    }
}
