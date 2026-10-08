package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Validates reachable invokedynamic sites and registers the dependencies of lambda sites. */
final class InvokeDynamicSupport {
    private final LambdaResolver lambdaResolver = new LambdaResolver();
    private final StringConcatResolver stringConcatResolver = new StringConcatResolver();

    void register(LinkedMethod linked, Instruction instruction, Map<String, JavaClass> classes,
                  Set<String> instantiatedClasses, Map<LambdaCallSite, LambdaSite> lambdaSites,
                  Consumer<MethodRef> implementationEnqueuer) {
        if (stringConcatResolver.supports(linked, instruction)) {
            stringConcatResolver.resolve(linked, instruction);
            return;
        }
        MethodRef caller = linked.method().reference();
        if (lambdaResolver.targetsInterface(linked, instruction, StructuredTaskSupport.JOINER)) {
            throw new CompileException(caller.displayName() + " at bytecode offset "
                    + instruction.offset() + ": Juno's JDK 27 StructuredTaskScope subset does not support "
                    + "custom Joiner implementations");
        }
        LambdaSite lambda = lambdaResolver.resolve(linked, instruction, classes);
        lambdaSites.put(lambda.callSite(), lambda);
        MethodRef implementation = lambda.implementation().method();
        if (lambda.implementation().referenceKind() == MethodHandleRef.REF_NEW_INVOKE_SPECIAL) {
            instantiatedClasses.add(implementation.owner());
        }
        if (IntrinsicRegistry.isIntrinsic(implementation)) {
            throw new CompileException(caller.displayName() + " at bytecode offset "
                    + instruction.offset() + ": method references to Juno intrinsics are not supported yet: "
                    + implementation.displayName());
        }
        implementationEnqueuer.accept(implementation);
    }
}
