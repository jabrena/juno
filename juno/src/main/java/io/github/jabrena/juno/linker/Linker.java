package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.ControlFlowGraphBuilder;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Performs closed-world reachability and resolves every static call before code generation. */
public final class Linker {
    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final ControlFlowGraphBuilder cfgBuilder = new ControlFlowGraphBuilder();

    public Program link(Map<String, JavaClass> classes, String mainClassName) {
        String internalName = mainClassName.replace('.', '/');
        JavaClass mainClass = classes.get(internalName);
        if (mainClass == null) {
            throw new CompileException("Main class not found on the classpath: " + mainClassName);
        }
        JavaMethod main = findMain(mainClass);
        MethodRef entryPoint = main.reference();
        Set<String> enumClassNames = classes.values().stream()
                .filter(JavaClass::isEnum)
                .map(JavaClass::name)
                .collect(Collectors.toUnmodifiableSet());

        Map<MethodRef, LinkedMethod> reachable = new LinkedHashMap<>();
        Deque<MethodRef> work = new ArrayDeque<>();
        work.add(entryPoint);
        while (!work.isEmpty()) {
            MethodRef reference = work.removeFirst();
            if (reachable.containsKey(reference)) {
                continue;
            }
            JavaClass owner = classes.get(reference.owner());
            if (owner == null) {
                throw new CompileException("Reachable class not found: " + reference.owner().replace('/', '.'));
            }
            JavaMethod method = owner.findMethod(reference.name(), reference.descriptor());
            if (method == null) {
                throw new CompileException("Reachable method not found: " + reference.displayName());
            }
            validateMethod(method, reference.equals(entryPoint), enumClassNames);
            List<Instruction> instructions = decoder.decode(method);
            ControlFlowGraph cfg = cfgBuilder.build(method.reference().displayName(), instructions);
            reachable.put(reference, new LinkedMethod(owner, method, instructions, cfg));

            for (Instruction instruction : instructions) {
                if (instruction.opcode() == 182 || instruction.opcode() == 184) {
                    MethodRef called = owner.constantPool().methodRef(instruction.operandA());
                    boolean isRecordAccessor = instruction.opcode() == 182 && RecordSupport.isAccessorCall(classes, called);
                    if (instruction.opcode() == 182 && !IntrinsicRegistry.isIntrinsic(called) && !isRecordAccessor) {
                        throw new CompileException(method.reference().displayName() + " at bytecode offset "
                                + instruction.offset() + ": instance call is not a Juno intrinsic: "
                                + called.displayName());
                    }
                    if (!IntrinsicRegistry.isIntrinsic(called) && !isRecordAccessor) {
                        work.addLast(called);
                    }
                }
            }
        }
        return new Program(entryPoint, List.copyOf(reachable.values()), classes);
    }

    private JavaMethod findMain(JavaClass mainClass) {
        JavaMethod conventional = mainClass.findMethod("main", "([Ljava/lang/String;)V");
        JavaMethod embedded = mainClass.findMethod("main", "()V");
        JavaMethod result = conventional != null ? conventional : embedded;
        if (result == null || !result.isStatic()) {
            throw new CompileException("Main class must declare static void main(String[]) or static void main()");
        }
        return result;
    }

    private void validateMethod(JavaMethod method, boolean entryPoint, Set<String> enumClassNames) {
        if (!method.isStatic()) {
            throw new CompileException("Juno v0.1 supports only static methods: " + method.reference().displayName());
        }
        if (method.isNative() || method.code() == null) {
            throw new CompileException("Native method has no Juno intrinsic: " + method.reference().displayName());
        }
        Descriptor descriptor = Descriptor.parse(method.descriptor());
        boolean conventionalMain = entryPoint
                && descriptor.parameters().equals(List.of("[Ljava/lang/String;"))
                && descriptor.returnsVoid();
        if (!conventionalMain && !descriptor.usesOnlyV01Types(enumClassNames)) {
            throw new CompileException("Juno v0.1 methods may use only int-like parameters and return values: "
                    + method.reference().displayName());
        }
    }
}
