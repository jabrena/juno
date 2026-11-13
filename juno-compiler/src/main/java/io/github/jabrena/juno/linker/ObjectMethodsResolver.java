package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.BootstrapMethod;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.InvokeDynamicRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves the fixed, javac-produced {@code ObjectMethods.bootstrap} protocol for records. */
public final class ObjectMethodsResolver {
    private static final MethodRef BOOTSTRAP = new MethodRef(
            "java/lang/runtime/ObjectMethods", "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;"
                    + "Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;");

    public boolean supports(LinkedMethod linked, Instruction instruction) {
        return bootstrapHandle(linked, instruction).method().equals(BOOTSTRAP);
    }

    public ObjectMethodsSite resolve(LinkedMethod linked, Instruction instruction, Map<String, JavaClass> classes) {
        ConstantPool pool = linked.owner().constantPool();
        InvokeDynamicRef dynamic = pool.invokeDynamic(instruction.operandA());
        MethodHandleRef bootstrapHandle = bootstrapHandle(linked, instruction);
        if (!bootstrapHandle.method().equals(BOOTSTRAP)) {
            throw error(linked, instruction, "unsupported invokedynamic bootstrap "
                    + bootstrapHandle.method().displayName());
        }
        if (bootstrapHandle.referenceKind() != MethodHandleRef.REF_INVOKE_STATIC) {
            throw error(linked, instruction, "ObjectMethods.bootstrap must be invokestatic");
        }

        BootstrapMethod bootstrap = linked.owner().bootstrapMethod(dynamic.bootstrapMethodIndex());
        if (bootstrap.argumentIndexes().size() < 2) {
            throw error(linked, instruction, "ObjectMethods.bootstrap requires a record class and component names");
        }
        String className = pool.className(bootstrap.argumentIndexes().getFirst());
        JavaClass record = classes.get(className);
        if (record == null || !record.isRecord()) {
            throw error(linked, instruction, "ObjectMethods.bootstrap class is not an available record: "
                    + className.replace('/', '.'));
        }
        ObjectMethodsSite.Operation operation = operation(dynamic.name(), dynamic.descriptor(), className,
                linked, instruction);
        String names = pool.string(bootstrap.argumentIndexes().get(1));
        List<String> componentNames = names.isEmpty() ? List.of() : List.of(names.split(";", -1));
        if (bootstrap.argumentIndexes().size() != componentNames.size() + 2) {
            throw error(linked, instruction, "ObjectMethods.bootstrap component-name and getter counts differ");
        }

        List<ObjectMethodsSite.Component> components = new ArrayList<>();
        for (int index = 0; index < componentNames.size(); index++) {
            String componentName = componentNames.get(index);
            FieldRef field = pool.fieldHandle(bootstrap.argumentIndexes().get(index + 2)).field();
            if (!field.owner().equals(className) || !field.name().equals(componentName)) {
                throw error(linked, instruction, "ObjectMethods.bootstrap getter does not match record component "
                        + componentName);
            }
            FieldInfo declaration = record.findField(field.name(), field.descriptor());
            if (declaration == null || declaration.isStatic()) {
                throw error(linked, instruction, "ObjectMethods.bootstrap component is not an instance field: "
                        + field.displayName());
            }
            components.add(new ObjectMethodsSite.Component(componentName, field.descriptor(), field));
            validateComponent(operation, className, componentName, field.descriptor(), classes, linked, instruction);
        }
        if (components.size() != record.recordComponents().size()) {
            throw error(linked, instruction, "ObjectMethods.bootstrap does not describe every record component");
        }
        return new ObjectMethodsSite(className, operation, components);
    }

    private ObjectMethodsSite.Operation operation(String name, String descriptor, String className,
                                                   LinkedMethod linked, Instruction instruction) {
        String expectedToString = "(L" + className + ";)Ljava/lang/String;";
        String expectedHashCode = "(L" + className + ";)I";
        String expectedEquals = "(L" + className + ";Ljava/lang/Object;)Z";
        if (name.equals(ObjectMethodsSite.Operation.TO_STRING.methodName()) && descriptor.equals(expectedToString)) {
            return ObjectMethodsSite.Operation.TO_STRING;
        }
        if (name.equals(ObjectMethodsSite.Operation.HASH_CODE.methodName()) && descriptor.equals(expectedHashCode)) {
            return ObjectMethodsSite.Operation.HASH_CODE;
        }
        if (name.equals(ObjectMethodsSite.Operation.EQUALS.methodName()) && descriptor.equals(expectedEquals)) {
            return ObjectMethodsSite.Operation.EQUALS;
        }
        throw error(linked, instruction, "unsupported ObjectMethods operation " + name + descriptor);
    }

    private void validateComponent(ObjectMethodsSite.Operation operation, String className, String componentName,
                                   String descriptor, Map<String, JavaClass> classes, LinkedMethod linked,
                                   Instruction instruction) {
        boolean primitive = descriptor.length() == 1 && "ZBCSIJFD".contains(descriptor);
        boolean string = Descriptor.isString(descriptor);
        JavaClass nestedType = descriptor.startsWith("L") && descriptor.endsWith(";")
                ? classes.get(descriptor.substring(1, descriptor.length() - 1)) : null;
        boolean nestedRecord = nestedType != null && nestedType.isRecord();
        boolean supported = switch (operation) {
            case TO_STRING -> primitive || string || nestedRecord;
            case HASH_CODE -> primitive || string;
            case EQUALS -> primitive || string || nestedRecord;
        };
        if (!supported) {
            throw error(linked, instruction, "record " + className.replace('/', '.') + " component "
                    + componentName + " has unsupported type " + descriptor + " for "
                    + operation.methodName());
        }
    }

    private MethodHandleRef bootstrapHandle(LinkedMethod linked, Instruction instruction) {
        ConstantPool pool = linked.owner().constantPool();
        InvokeDynamicRef dynamic = pool.invokeDynamic(instruction.operandA());
        BootstrapMethod bootstrap = linked.owner().bootstrapMethod(dynamic.bootstrapMethodIndex());
        return pool.methodHandle(bootstrap.methodHandleIndex());
    }

    private CompileException error(LinkedMethod linked, Instruction instruction, String detail) {
        return new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                + instruction.offset() + ": " + detail);
    }
}
