package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Resolves reachable {@code invokeinterface} sites against the instantiated closed-world classes. */
final class InterfaceDispatchResolver {
    private final ScopedValueSupport scopedValueSupport = new ScopedValueSupport();

    private final StructuredTaskSupport structuredTaskSupport = new StructuredTaskSupport();


    void validateCall(LinkedMethod linked, Instruction instruction, MethodRef called,
                      Map<String, JavaClass> classes, java.util.Collection<LambdaSite> lambdaSites) {
        JavaClass interfaceClass = classes.get(called.owner());
        boolean lambdaInterface = lambdaSites.stream()
                .anyMatch(site -> site.interfaceMethod().equals(called));
        if ((interfaceClass == null && !lambdaInterface && !called.equals(ThreadSupport.RUNNABLE_RUN)
                && !structuredTaskSupport.isCallableCall(called)
                && !scopedValueSupport.isCallableOpCall(called))
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

    void validateVirtualCall(LinkedMethod linked, Instruction instruction, MethodRef called,
                             Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(called.owner());
        if (owner == null || owner.isInterface()) {
            throw new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                    + instruction.offset() + ": invokevirtual owner is not an available class: "
                    + called.owner().replace('/', '.'));
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
        JavaClass owner = classes.get(interfaceMethod.owner());
        boolean interfaceCall = owner == null || owner.isInterface();
        List<InterfaceDispatch.Target> targets = new ArrayList<>();
        for (String className : instantiatedClasses) {
            JavaClass candidate = classes.get(className);
            if (candidate == null || candidate.isInterface()
                    || (interfaceCall
                    ? !implementsInterface(className, interfaceMethod.owner(), classes, new TreeSet<>())
                    : !isSubclass(className, interfaceMethod.owner(), classes, new TreeSet<>()))) {
                continue;
            }
            JavaMethod implementation = implementation(className, interfaceMethod, classes, interfaceCall,
                    new TreeSet<>());
            if (implementation == null || implementation.isAbstract()) {
                throw new CompileException("Reachable interface implementation " + className.replace('/', '.')
                        + " must provide concrete method " + interfaceMethod.name()
                        + interfaceMethod.descriptor());
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

    private @Nullable JavaMethod implementation(String className, MethodRef call, Map<String, JavaClass> classes,
                                                boolean interfaceCall, Set<String> visited) {
        JavaClass type = classes.get(className);
        if (type == null || !visited.add(className)) {
            return null;
        }
        JavaMethod declared = type.findMethod(call.name(), call.descriptor());
        if (declared != null) {
            return declared;
        }
        if (type.superClassName() != null) {
            JavaMethod inherited = implementation(type.superClassName(), call, classes, interfaceCall, visited);
            if (inherited != null) {
                return inherited;
            }
        }
        if (interfaceCall) {
            for (String direct : type.interfaces()) {
                JavaMethod defaultMethod = implementation(direct, call, classes, true, visited);
                if (defaultMethod != null) {
                    return defaultMethod;
                }
            }
        }
        return null;
    }

    private boolean isSubclass(String typeName, String expected, Map<String, JavaClass> classes,
                               Set<String> visited) {
        if (!visited.add(typeName)) {
            return false;
        }
        if (typeName.equals(expected)) {
            return true;
        }
        JavaClass type = classes.get(typeName);
        return type != null && type.superClassName() != null
                && isSubclass(type.superClassName(), expected, classes, visited);
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
