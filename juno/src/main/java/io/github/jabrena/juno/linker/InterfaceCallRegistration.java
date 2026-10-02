package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Collection;
import java.util.Map;

/** Keeps intrinsic interface calls out of ordinary closed-world dispatch resolution. */
final class InterfaceCallRegistration {
    private InterfaceCallRegistration() {
    }

    static void register(LinkedMethod linked, Instruction instruction, MethodRef called,
                         Map<String, JavaClass> classes, Collection<LambdaSite> lambdaSites,
                         Map<InterfaceCallSite, MethodRef> interfaceCalls,
                         InterfaceDispatchResolver resolver) {
        if (IntrinsicRegistry.isIntrinsic(called)) return;
        resolver.validateCall(linked, instruction, called, classes, lambdaSites);
        interfaceCalls.put(new InterfaceCallSite(linked.method().reference(), instruction.offset()), called);
    }
}
