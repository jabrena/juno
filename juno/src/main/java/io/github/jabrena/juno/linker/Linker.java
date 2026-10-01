package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.ControlFlowGraphBuilder;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Performs closed-world reachability and resolves every static call before code generation. */
public final class Linker {
    /** The on-board {@link Capability} each capability-dependent intrinsic needs; every other intrinsic is portable. */
    private static final Map<Intrinsic, Capability> REQUIRED_CAPABILITIES = requiredCapabilities();
    private static final MethodRef DRAW_TEXT_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawText", "([[ZLjava/lang/String;II)V");
    private static final MethodRef DRAW_CHAR_METHOD = new MethodRef("io/github/jabrena/juno/api/led/LedCanvas",
            "drawChar", "([[ZIII)V");

    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final ControlFlowGraphBuilder cfgBuilder = new ControlFlowGraphBuilder();
    private final InterfaceDispatchResolver interfaceDispatchResolver = new InterfaceDispatchResolver();
    private final ReachabilityClosure reachabilityClosure = new ReachabilityClosure();
    private final LambdaResolver lambdaResolver = new LambdaResolver();

    public Program link(Map<String, JavaClass> classes, String mainClassName) {
        return link(classes, mainClassName, Optional.empty());
    }

    /**
     * {@code requestedBoardId} (see {@link Board#fromId}) picks which of the entry point's declared
     * {@code @Board} targets this build compiles for; it must name one of them. It may be omitted only
     * when the entry point declares exactly one board (or none, defaulting to {@link Board#DEFAULT}) —
     * declaring more than one and omitting it is a {@link CompileException}, since silently picking one
     * would make the build depend on declaration order.
     */
    public Program link(Map<String, JavaClass> classes, String mainClassName, Optional<String> requestedBoardId) {
        String internalName = mainClassName.replace('.', '/');
        JavaClass mainClass = classes.get(internalName);
        if (mainClass == null) {
            throw new CompileException("Main class not found on the classpath: " + mainClassName);
        }
        List<Board> declaredBoards = declaredBoards(mainClass);
        Board board = resolveBoard(declaredBoards, requestedBoardId);
        JavaMethod main = findMain(mainClass);
        MethodRef entryPoint = main.reference();
        Set<String> enumClassNames = classes.values().stream()
                .filter(JavaClass::isEnum)
                .map(JavaClass::name)
                .collect(Collectors.toUnmodifiableSet());

        JavaMethod mainInitializer = mainClass.findMethod("<clinit>", "()V");
        ReachabilityClosure.Result reachability = reachabilityClosure.resolve(entryPoint,
                mainInitializer == null ? null : mainInitializer.reference(), classes,
                reference -> linkMethod(classes, reference, reference.equals(entryPoint)),
                (linked, work, calls, instantiated, lambdas) -> enqueueDependencies(linked, declaredBoards, classes,
                        work, calls, instantiated, lambdas), interfaceDispatchResolver,
                method -> hasReachableBody(method, classes),
                (dispatch, method) -> validateInterfaceTargetCapability(dispatch, method, declaredBoards));
        for (Map.Entry<InterfaceCallSite, MethodRef> call : reachability.interfaceCalls().entrySet()) {
            if (!reachability.interfaceDispatches().containsKey(call.getKey())) {
                throw new CompileException(call.getKey().caller().displayName() + " at bytecode offset "
                        + call.getKey().bytecodeOffset() + ": no reachable implementation of "
                        + call.getValue().displayName());
            }
        }
        if (mainClass.watchdogTimeoutMillis().isPresent()) {
            requireCapability(declaredBoards, Capability.WATCHDOG, "");
        }
        return new Program(entryPoint, reachability.methods(), classes, board,
                mainClass.watchdogTimeoutMillis(), reachability.interfaceDispatches(), reachability.lambdaSites());
    }

    private void validateInterfaceTargetCapability(InterfaceDispatch dispatch, MethodRef method,
                                                   List<Board> declaredBoards) {
        IntrinsicRegistry.resolve(method).map(REQUIRED_CAPABILITIES::get).ifPresent(capability ->
                requireCapability(declaredBoards, capability,
                        " (used through " + dispatch.interfaceMethod().displayName() + ")"));
    }

    /** Every board the entry point's {@code @Board} annotation names, or just {@link Board#DEFAULT} if absent. */
    private List<Board> declaredBoards(JavaClass mainClass) {
        List<String> boardApiClassNames = mainClass.boardApiClassNames();
        return boardApiClassNames.isEmpty()
                ? List.of(Board.DEFAULT)
                : boardApiClassNames.stream().map(Board::fromApiClassName).distinct().toList();
    }

    /** Picks the single board this build targets out of {@code declaredBoards} (see {@link #link}). */
    private Board resolveBoard(List<Board> declaredBoards, Optional<String> requestedBoardId) {
        if (requestedBoardId.isPresent()) {
            Board requested = Board.fromId(requestedBoardId.get());
            if (!declaredBoards.contains(requested)) {
                throw new CompileException("Requested board '" + requestedBoardId.get()
                        + "' is not one of @Board's declared boards: " + displayNames(declaredBoards));
            }
            return requested;
        }
        if (declaredBoards.size() > 1) {
            throw new CompileException("@Board declares multiple boards (" + displayNames(declaredBoards)
                    + "); pass the target board explicitly: -Djuno.board=<id> for the Maven plugin, "
                    + "--board=<id> for the standalone CLI");
        }
        return declaredBoards.get(0);
    }

    private static String displayNames(List<Board> boards) {
        return boards.stream().map(Board::displayName).collect(Collectors.joining(", "));
    }

    private LinkedMethod linkMethod(Map<String, JavaClass> classes, MethodRef reference, boolean entryPoint) {
        JavaClass owner = classes.get(reference.owner());
        if (owner == null) {
            throw new CompileException("Reachable class not found: " + reference.owner().replace('/', '.'));
        }
        JavaMethod method = owner.findMethod(reference.name(), reference.descriptor());
        if (method == null) {
            throw new CompileException("Reachable method not found: " + reference.displayName());
        }
        validateMethod(method, entryPoint, classes.keySet());
        List<Instruction> instructions = decoder.decode(method);
        ControlFlowGraph cfg = cfgBuilder.build(method.reference().displayName(), instructions,
                method.exceptionHandlers());
        return new LinkedMethod(owner, method, instructions, cfg);
    }

    /** Queues every method and static initializer {@code linked}'s invoke/static-field instructions reach. */
    private void enqueueDependencies(LinkedMethod linked, List<Board> declaredBoards, Map<String, JavaClass> classes,
            Deque<MethodRef> work, Map<InterfaceCallSite, MethodRef> interfaceCalls,
            Set<String> instantiatedClasses, Map<LambdaCallSite, LambdaSite> lambdaSites) {
        JavaClass owner = linked.owner();
        MethodRef caller = linked.method().reference();
        for (Instruction instruction : linked.instructions()) {
            int opcode = instruction.opcode();
            if (opcode == 182 || opcode == 183 || opcode == 184) {
                enqueueCall(owner.constantPool().methodRef(instruction.operandA()), caller, declaredBoards, classes,
                        work);
            }
            if (opcode == 185) {
                MethodRef called = owner.constantPool().methodRef(instruction.operandA());
                interfaceDispatchResolver.validateCall(linked, instruction, called, classes, lambdaSites.values());
                interfaceCalls.put(new InterfaceCallSite(caller, instruction.offset()), called);
            }
            if (opcode == 186) {
                LambdaSite lambda = lambdaResolver.resolve(linked, instruction, classes);
                lambdaSites.put(lambda.callSite(), lambda);
                MethodRef implementation = lambda.implementation().method();
                if (lambda.implementation().referenceKind()
                        == io.github.jabrena.juno.classfile.MethodHandleRef.REF_NEW_INVOKE_SPECIAL) {
                    instantiatedClasses.add(implementation.owner());
                }
                if (IntrinsicRegistry.isIntrinsic(implementation)) {
                    throw new CompileException(caller.displayName() + " at bytecode offset "
                            + instruction.offset() + ": method references to Juno intrinsics are not supported yet: "
                            + implementation.displayName());
                }
                enqueueCall(implementation, caller, declaredBoards, classes, work);
            }
            if (opcode == 187) {
                instantiatedClasses.add(owner.constantPool().className(instruction.operandA()));
            }
            if (opcode == 178 || opcode == 179) {
                enqueueStaticInitializer(owner.constantPool().fieldRef(instruction.operandA()), classes, work);
            }
        }
    }

    private void enqueueCall(MethodRef called, MethodRef caller, List<Board> declaredBoards,
            Map<String, JavaClass> classes, Deque<MethodRef> work) {
        IntrinsicRegistry.resolve(called).map(REQUIRED_CAPABILITIES::get).ifPresent(capability ->
                requireCapability(declaredBoards, capability, " (used from " + caller.displayName() + ")"));
        if (called.equals(DRAW_TEXT_METHOD)) {
            work.addLast(DRAW_CHAR_METHOD);
        } else if (hasReachableBody(called, classes)) {
            work.addLast(called);
        }
    }

    /** Whether {@code called} is an ordinary method whose bytecode must be linked, not one Juno lowers itself. */
    private boolean hasReachableBody(MethodRef called, Map<String, JavaClass> classes) {
        return !IntrinsicRegistry.isIntrinsic(called)
                && !isRuntimeBaseConstructor(called)
                && !ThrowableTypes.isBuiltInConstructor(called)
                && !ThrowableTypes.isGetMessage(called, classes)
                && !isEnumOperation(classes, called)
                && !isCompileTimeGetenv(called)
                && !SupportedJdkMethods.isRequireNonNull(called);
    }

    private void enqueueStaticInitializer(FieldRef field, Map<String, JavaClass> classes, Deque<MethodRef> work) {
        JavaClass fieldOwner = classes.get(field.owner());
        if (fieldOwner == null || fieldOwner.isEnum() || field.name().startsWith("$SwitchMap$")) {
            return;
        }
        JavaMethod initializer = fieldOwner.findMethod("<clinit>", "()V");
        if (initializer != null) {
            work.addLast(initializer.reference());
        }
    }

    /**
     * Every declared board must provide {@code capability}, not just the one this build targets, so a
     * portable program never compiles for one of its boards and fails on another.
     */
    private void requireCapability(List<Board> declaredBoards, Capability capability, String context) {
        declaredBoards.stream().filter(board -> !board.supports(capability)).findFirst().ifPresent(board -> {
            throw new CompileException(capability.apiName() + " requires @Board("
                    + Arrays.stream(Board.values()).filter(candidate -> candidate.supports(capability))
                            .map(Board::annotationArgument).collect(Collectors.joining(" or "))
                    + "): " + capability.unsupportedReason(board) + context);
        });
    }

    private static Map<Intrinsic, Capability> requiredCapabilities() {
        Map<Intrinsic, Capability> required = new EnumMap<>(Intrinsic.class);
        for (Intrinsic intrinsic : List.of(Intrinsic.LED_MATRIX_BEGIN, Intrinsic.LED_MATRIX_LOAD_FRAME,
                Intrinsic.LED_MATRIX_CLEAR)) {
            required.put(intrinsic, Capability.LED_MATRIX);
        }
        for (Intrinsic intrinsic : List.of(Intrinsic.WIFI_BEGIN, Intrinsic.WIFI_STATUS, Intrinsic.WIFI_LOCAL_IP,
                Intrinsic.UDP_LISTEN, Intrinsic.UDP_SEND, Intrinsic.UDP_BROADCAST, Intrinsic.UDP_RECEIVE,
                Intrinsic.UDP_STOP)) {
            required.put(intrinsic, Capability.WIFI);
        }
        for (Intrinsic intrinsic : List.of(Intrinsic.HTTPS_GET, Intrinsic.HTTPS_GET_PATH_BUFFER,
                Intrinsic.HTTPS_POST, Intrinsic.HTTPS_DELETE, Intrinsic.HTTPS_PATCH, Intrinsic.HTTPS_QUERY,
                Intrinsic.SMTP_SEND_TLS, Intrinsic.POP3_MESSAGE_COUNT, Intrinsic.POP3_READ_LATEST,
                Intrinsic.POP3_READ_SUBJECT)) {
            required.put(intrinsic, Capability.HTTPS_CLIENT);
        }
        for (Intrinsic intrinsic : List.of(Intrinsic.HTTP_GET, Intrinsic.HTTP_POST, Intrinsic.HTTP_DELETE,
                Intrinsic.HTTP_PATCH, Intrinsic.HTTP_QUERY, Intrinsic.HTTP_SERVER_BEGIN,
                Intrinsic.HTTP_SERVER_ACCEPT, Intrinsic.HTTP_SERVER_METHOD, Intrinsic.HTTP_SERVER_PATH,
                Intrinsic.HTTP_SERVER_RESPOND, Intrinsic.HTTP_SERVER_RESPOND_BUILDER, Intrinsic.SMTP_SEND)) {
            required.put(intrinsic, Capability.WIFI_S3_NETWORKING);
        }
        return Collections.unmodifiableMap(required);
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
