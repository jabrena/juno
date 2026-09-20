package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.ControlFlowGraphBuilder;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Performs closed-world reachability and resolves every static call before code generation. */
public final class Linker {
    private static final Set<Intrinsic> LED_MATRIX_INTRINSICS = EnumSet.of(
            Intrinsic.LED_MATRIX_BEGIN, Intrinsic.LED_MATRIX_LOAD_FRAME, Intrinsic.LED_MATRIX_CLEAR);
    private static final Set<Intrinsic> WIFI_INTRINSICS = EnumSet.of(Intrinsic.WIFI_BEGIN, Intrinsic.WIFI_STATUS,
            Intrinsic.HTTP_GET, Intrinsic.HTTP_POST, Intrinsic.HTTP_DELETE, Intrinsic.HTTP_PATCH,
            Intrinsic.HTTP_QUERY);
    private static final MethodRef DRAW_TEXT_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawText", "([[ZLjava/lang/String;II)V");
    private static final MethodRef DRAW_CHAR_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawChar", "([[ZIII)V");

    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final ControlFlowGraphBuilder cfgBuilder = new ControlFlowGraphBuilder();

    public Program link(Map<String, JavaClass> classes, String mainClassName) {
        String internalName = mainClassName.replace('.', '/');
        JavaClass mainClass = classes.get(internalName);
        if (mainClass == null) {
            throw new CompileException("Main class not found on the classpath: " + mainClassName);
        }
        Board board = mainClass.boardApiClassName().map(Board::fromApiClassName).orElse(Board.DEFAULT);
        JavaMethod main = findMain(mainClass);
        MethodRef entryPoint = main.reference();
        Set<String> enumClassNames = classes.values().stream()
                .filter(JavaClass::isEnum)
                .map(JavaClass::name)
                .collect(Collectors.toUnmodifiableSet());

        Map<MethodRef, LinkedMethod> reachable = new LinkedHashMap<>();
        Deque<MethodRef> work = new ArrayDeque<>();
        JavaMethod mainInitializer = mainClass.findMethod("<clinit>", "()V");
        if (mainInitializer != null) {
            work.add(mainInitializer.reference());
        }
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
            validateMethod(method, reference.equals(entryPoint), classes.keySet());
            List<Instruction> instructions = decoder.decode(method);
            ControlFlowGraph cfg = cfgBuilder.build(method.reference().displayName(), instructions);
            reachable.put(reference, new LinkedMethod(owner, method, instructions, cfg));

            for (Instruction instruction : instructions) {
                if (instruction.opcode() == 182 || instruction.opcode() == 183 || instruction.opcode() == 184) {
                    MethodRef called = owner.constantPool().methodRef(instruction.operandA());
                    IntrinsicRegistry.resolve(called).ifPresent(intrinsic -> requireLedMatrixSupport(board, intrinsic, reference));
                    IntrinsicRegistry.resolve(called).ifPresent(intrinsic -> requireWifiSupport(board, intrinsic, reference));
                    if (isDrawTextCall(called)) {
                        work.addLast(DRAW_CHAR_METHOD);
                    } else if (!IntrinsicRegistry.isIntrinsic(called)
                            && !isRuntimeBaseConstructor(called)
                            && !isEnumOperation(classes, called)
                            && !isCompileTimeGetenv(called)) {
                        work.addLast(called);
                    }
                }
                if (instruction.opcode() == 178 || instruction.opcode() == 179) {
                    FieldRef field = owner.constantPool().fieldRef(instruction.operandA());
                    JavaClass fieldOwner = classes.get(field.owner());
                    if (fieldOwner != null && !fieldOwner.isEnum() && !field.name().startsWith("$SwitchMap$")) {
                        JavaMethod initializer = fieldOwner.findMethod("<clinit>", "()V");
                        if (initializer != null) {
                            work.addLast(initializer.reference());
                        }
                    }
                }
            }
        }
        return new Program(entryPoint, List.copyOf(reachable.values()), classes, board);
    }

    private void requireLedMatrixSupport(Board board, Intrinsic intrinsic, MethodRef caller) {
        if (!board.hasLedMatrix() && LED_MATRIX_INTRINSICS.contains(intrinsic)) {
            throw new CompileException("LedMatrix requires @Board(ArduinoUnoR4WiFi.class): " + board.displayName()
                    + " has no onboard LED matrix (used from " + caller.displayName() + ")");
        }
    }

    private void requireWifiSupport(Board board, Intrinsic intrinsic, MethodRef caller) {
        if (!board.hasWifi() && WIFI_INTRINSICS.contains(intrinsic)) {
            throw new CompileException("Wifi requires @Board(ArduinoUnoR4WiFi.class): " + board.displayName()
                    + " has no onboard WiFi module (used from " + caller.displayName() + ")");
        }
    }

    /**
     * {@code System.getenv("NAME")} of a literal environment-variable name is resolved by Juno itself at
     * compile time (see {@code BytecodeToIr#lowerCompileTimeGetenv}), reading its own build-time
     * environment rather than the target device's — never a reachable call.
     */
    private boolean isCompileTimeGetenv(MethodRef called) {
        return called.owner().equals("java/lang/System") && called.name().equals("getenv")
                && called.descriptor().equals("(Ljava/lang/String;)Ljava/lang/String;");
    }

    /**
     * {@code LedCanvas.drawText(frame, "literal", x, y)} is unrolled by {@code BytecodeToIr} into one
     * {@code LedCanvas.drawChar} call per character at compile time — {@code drawText} itself is
     * native (no body to walk into); {@link #DRAW_CHAR_METHOD} is what's actually reachable.
     */
    private boolean isDrawTextCall(MethodRef called) {
        return called.equals(DRAW_TEXT_METHOD);
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

    private void validateMethod(JavaMethod method, boolean entryPoint, Set<String> referenceClassNames) {
        if (method.isNative() || method.code() == null) {
            throw new CompileException("Native method has no Juno intrinsic: " + method.reference().displayName());
        }
        Descriptor descriptor = Descriptor.parse(method.descriptor());
        boolean conventionalMain = entryPoint
                && descriptor.parameters().equals(List.of("[Ljava/lang/String;"))
                && descriptor.returnsVoid();
        if (!conventionalMain && !descriptor.usesOnlyV01Types(referenceClassNames)) {
            throw new CompileException("Juno methods may use only supported scalar, array, or closed-world reference parameters and returns: "
                    + method.reference().displayName());
        }
    }

    private boolean isRuntimeBaseConstructor(MethodRef called) {
        return called.name().equals("<init>") && called.descriptor().endsWith(")V")
                && (called.owner().startsWith("java/lang/")
                        || called.owner().equals("java/lang/Enum"));
    }

    private boolean isEnumOperation(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        if (owner != null && owner.isEnum()) {
            return (called.name().equals("ordinal") && called.descriptor().equals("()I"))
                    || (called.name().equals("values") && called.descriptor().startsWith("()[L"));
        }
        return called.owner().equals("java/lang/Enum")
                && called.name().equals("ordinal") && called.descriptor().equals("()I");
    }
}
