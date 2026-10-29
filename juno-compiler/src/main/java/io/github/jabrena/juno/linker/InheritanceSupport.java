package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;

/** Detects the closed-world inheritance features that require the board inheritance capability. */
final class InheritanceSupport {

    boolean usesClassInheritance(ReachabilityClosure.Result reachability, Map<String, JavaClass> classes) {
        if (reachability.instantiatedClasses().stream().anyMatch(className -> hasUserSuperclass(className, classes))) {
            return true;
        }
        return reachability.interfaceDispatches().values().stream()
                .flatMap(dispatch -> dispatch.targets().stream())
                .filter(target -> !target.isLambda())
                .anyMatch(target -> !target.method().owner().equals(target.className())
                        || isDefaultInterfaceMethod(target.method(), classes));
    }

    private boolean hasUserSuperclass(String className, Map<String, JavaClass> classes) {
        JavaClass type = classes.get(className);
        while (type != null && type.superClassName() != null
                && !"java/lang/Object".equals(type.superClassName())) {
            JavaClass parent = classes.get(type.superClassName());
            if (parent == null) {
                return false;
            }
            if (!parent.name().startsWith("java/")) {
                return true;
            }
            type = parent;
        }
        return false;
    }

    private boolean isDefaultInterfaceMethod(MethodRef method, Map<String, JavaClass> classes) {
        JavaClass owner = classes.get(method.owner());
        JavaMethod declaration = owner == null ? null : owner.findMethod(method.name(), method.descriptor());
        return owner != null && owner.isInterface() && declaration != null && !declaration.isAbstract();
    }
}
