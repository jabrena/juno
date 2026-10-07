package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.BootstrapMethod;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.InvokeDynamicRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.Map;

/** Validates and resolves the deliberately small, javac-produced LambdaMetafactory protocol. */
final class LambdaResolver {
    private static final MethodRef METAFACTORY = new MethodRef("java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");

    LambdaSite resolve(LinkedMethod linked, Instruction instruction, Map<String, JavaClass> classes) {
        ConstantPool pool = linked.owner().constantPool();
        InvokeDynamicRef dynamic = pool.invokeDynamic(instruction.operandA());
        BootstrapMethod bootstrap = linked.owner().bootstrapMethod(dynamic.bootstrapMethodIndex());
        MethodHandleRef bootstrapHandle = pool.methodHandle(bootstrap.methodHandleIndex());
        if (!bootstrapHandle.method().equals(METAFACTORY)) {
            throw error(linked, instruction, "only LambdaMetafactory.metafactory lambdas/method references and "
                    + "StringConcatFactory.makeConcatWithConstants concatenation are supported");
        }
        if (bootstrap.argumentIndexes().size() != 3) {
            throw error(linked, instruction, "LambdaMetafactory.metafactory requires exactly three bootstrap "
                    + "arguments");
        }

        String samDescriptor = pool.methodType(bootstrap.argumentIndexes().get(0));
        MethodHandleRef implementation = pool.methodHandle(bootstrap.argumentIndexes().get(1));
        String instantiatedDescriptor = pool.methodType(bootstrap.argumentIndexes().get(2));
        if (!implementation.isSupportedInvocation()) {
            throw error(linked, instruction, "unsupported lambda implementation method-handle kind "
                    + implementation.referenceKind());
        }

        Descriptor factory = Descriptor.parse(dynamic.descriptor());
        String interfaceName = referenceName(factory.returnType());
        JavaClass functionalInterface = classes.get(interfaceName);
        if (functionalInterface != null && !functionalInterface.isInterface()) {
            throw error(linked, instruction, "lambda result is not an available interface: "
                    + factory.returnType());
        }
        JavaMethod sam = functionalInterface == null ? null
                : functionalInterface.findMethod(dynamic.name(), samDescriptor);
        if (functionalInterface != null && (sam == null || !sam.isAbstract())) {
            throw error(linked, instruction, "functional interface does not declare abstract method "
                    + dynamic.name() + samDescriptor);
        }
        if (functionalInterface == null && !interfaceName.startsWith("java/")) {
            throw error(linked, instruction, "lambda result is not an available interface: "
                    + factory.returnType());
        }

        validateTypes(linked, instruction, factory, Descriptor.parse(instantiatedDescriptor), implementation,
                classes.keySet());
        MethodRef caller = linked.method().reference();
        String syntheticName = caller.owner() + "$$JunoLambda$"
                + Integer.toUnsignedString(caller.hashCode(), 16) + "$" + instruction.offset();
        return new LambdaSite(new LambdaCallSite(caller, instruction.offset()), syntheticName,
                new MethodRef(interfaceName, dynamic.name(), samDescriptor), implementation,
                factory.parameters(), instantiatedDescriptor);
    }

    boolean targetsInterface(LinkedMethod linked, Instruction instruction, String interfaceName) {
        InvokeDynamicRef dynamic = linked.owner().constantPool().invokeDynamic(instruction.operandA());
        return referenceName(Descriptor.parse(dynamic.descriptor()).returnType()).equals(interfaceName);
    }

    private void validateTypes(LinkedMethod linked, Instruction instruction, Descriptor factory,
                               Descriptor instantiated, MethodHandleRef implementation,
                               java.util.Set<String> knownClasses) {
        if (!factory.parameters().stream().allMatch(type -> Descriptor.jvmSlots(type) == 1)
                || !instantiated.parameters().stream().allMatch(type -> Descriptor.jvmSlots(type) == 1)) {
            throw error(linked, instruction, "long and double lambda captures/parameters are not supported yet");
        }
        Descriptor capturesOnly = new Descriptor(factory.parameters(), "V");
        if (!capturesOnly.usesOnlyV01Types(knownClasses) || !instantiated.usesOnlyV01Types(knownClasses)) {
            throw error(linked, instruction, "lambda uses an unsupported capture, parameter, or return type");
        }
        Descriptor target = Descriptor.parse(implementation.method().descriptor());
        int supplied = factory.parameters().size() + instantiated.parameters().size();
        int required = target.parameters().size();
        if (implementation.referenceKind() == MethodHandleRef.REF_INVOKE_VIRTUAL
                || implementation.referenceKind() == MethodHandleRef.REF_INVOKE_SPECIAL
                || implementation.referenceKind() == MethodHandleRef.REF_INVOKE_INTERFACE) {
            required++;
        }
        if (supplied != required) {
            throw error(linked, instruction, "lambda implementation " + implementation.method().displayName()
                    + " needs " + required + " argument value(s), but captures plus invocation provide " + supplied);
        }
        if (implementation.referenceKind() == MethodHandleRef.REF_NEW_INVOKE_SPECIAL) {
            String expected = "L" + implementation.method().owner() + ";";
            if (!target.returnsVoid() || !compatible(instantiated.returnType(), expected)) {
                throw error(linked, instruction, "constructor reference return does not match " + expected);
            }
        } else if (!compatible(instantiated.returnType(), target.returnType())) {
            throw error(linked, instruction, "lambda implementation return " + target.returnType()
                    + " does not match " + instantiated.returnType());
        }
    }

    private boolean compatible(String from, String to) {
        return from.equals(to) || (isReference(from) && isReference(to));
    }

    private boolean isReference(String descriptor) {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    private String referenceName(String descriptor) {
        if (descriptor.length() > 2 && descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        throw new CompileException("invokedynamic lambda factory must return an interface reference: " + descriptor);
    }

    private CompileException error(LinkedMethod linked, Instruction instruction, String detail) {
        return new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                + instruction.offset() + ": " + detail);
    }
}
