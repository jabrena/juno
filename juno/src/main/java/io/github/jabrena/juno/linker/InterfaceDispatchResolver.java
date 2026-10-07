package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Resolves reachable {@code invokeinterface} sites against the instantiated closed-world classes. */
final class InterfaceDispatchResolver {

    void validateCall(LinkedMethod linked, Instruction instruction, MethodRef called,
                      Map<String, JavaClass> classes, java.util.Collection<LambdaSite> lambdaSites) {
        JavaClass interfaceClass = classes.get(called.owner());
        boolean lambdaInterface = lambdaSites.stream()
                .anyMatch(site -> site.interfaceMethod().equals(called));
        if ((interfaceClass == null && !lambdaInterface && !called.equals(ThreadSupport.RUNNABLE_RUN)
                && !StructuredTaskSupport.isCallableCall(called)
                && !ScopedValueSupport.isCallableOpCall(called))
                || (interfaceClass != null && !interfaceClass.isInterface())) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": invokeinterface owner is not an available interface: "
                    + called.owner().replace('/', '.'));
        }
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        int expectedCount = 1 + descriptor.parameters().stream().mapToInt(Descriptor::jvmSlots).sum();
        if (instruction.operandB() != expectedCount) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": invokeinterface argument count " + instruction.operandB()
                    + " does not match descriptor " + called.descriptor());
        }
    }

    Map<InterfaceCallSite, InterfaceDispatch> resolve(Map<InterfaceCallSite, MethodRef> calls,
            Set<String> instantiatedClasses, java.util.Collection<LambdaSite> lambdaSites,
            Map<String, JavaClass> classes) {
        Map<InterfaceCallSite, InterfaceDispatch> result = new LinkedHashMap<>();
        for (Map.Entry<InterfaceCallSite, MethodRef> call : calls.entrySet()) {
            MethodRef interfaceMethod = call.getValue();
            List<InterfaceDispatch.Target> targets = targets(interfaceMethod, instantiatedClasses, lambdaSites,
                    classes);
            if (!targets.isEmpty()) {
                result.put(call.getKey(), new InterfaceDispatch(interfaceMethod, targets));
            }
        }
        return result;
    }

    private List<InterfaceDispatch.Target> targets(MethodRef interfaceMethod, Set<String> instantiatedClasses,
                                                    java.util.Collection<LambdaSite> lambdaSites,
                                                    Map<String, JavaClass> classes) {
        List<InterfaceDispatch.Target> targets = new ArrayList<>();
        for (String className : instantiatedClasses) {
            JavaClass candidate = classes.get(className);
            if (candidate == null || candidate.isInterface()
                    || !implementsInterface(className, interfaceMethod.owner(), classes, new TreeSet<>())) {
                continue;
            }
            JavaMethod implementation = candidate.findMethod(interfaceMethod.name(), interfaceMethod.descriptor());
            if (implementation == null || implementation.isAbstract()) {
                throw new CompileException("Reachable interface implementation " + className.replace('/', '.')
                        + " must declare concrete method " + interfaceMethod.name()
                        + interfaceMethod.descriptor() + " directly (inherited/default implementations are "
                        + "not supported yet)");
            }
            targets.add(new InterfaceDispatch.Target(className, implementation.reference()));
        }
        lambdaSites.stream()
                .filter(site -> site.interfaceMethod().equals(interfaceMethod))
                .sorted(java.util.Comparator.comparing(LambdaSite::syntheticClassName))
                .map(InterfaceDispatch.Target::lambda)
                .forEach(targets::add);
        return targets;
    }

    private boolean implementsInterface(String typeName, String interfaceName, Map<String, JavaClass> classes,
                                        Set<String> visited) {
        if (!visited.add(typeName)) {
            return false;
        }
        JavaClass type = classes.get(typeName);
        if (type == null) {
            return false;
        }
        for (String direct : type.interfaces()) {
            if (direct.equals(interfaceName)
                    || implementsInterface(direct, interfaceName, classes, visited)) {
                return true;
            }
        }
        return type.superClassName() != null
                && implementsInterface(type.superClassName(), interfaceName, classes, visited);
    }
}
