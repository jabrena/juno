package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.ControlFlowGraphBuilder;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.BigNumberMethods;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Performs closed-world reachability and resolves every static call before code generation. */
public final class Linker {

    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private final SupportedJdkMethods supportedJdkMethods = new SupportedJdkMethods();

    private final InterfaceCallRegistration interfaceCallRegistration = new InterfaceCallRegistration();

    private final ThreadSupport threadSupport = new ThreadSupport();

    private final AtomicSupport atomicSupport = new AtomicSupport();

    private final LockSupport lockSupport = new LockSupport();

    private final ScopedValueSupport scopedValueSupport = new ScopedValueSupport();

    private final BigNumberSupport bigNumberSupport = new BigNumberSupport();

    private final UuidSupport uuidSupport = new UuidSupport();

    private final LocalDateTimeSupport localDateTimeSupport = new LocalDateTimeSupport();

    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    private final StructuredTaskSupport structuredTaskSupport = new StructuredTaskSupport();

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
    private final InvokeDynamicSupport invokeDynamicSupport = new InvokeDynamicSupport();

    public Program link(Map<String, JavaClass> classes, String mainClassName) {
        return link(classes, mainClassName, Optional.empty());
    }

    /**
     * {@code requestedBoardId} (see {@link Board#fromId}) picks which of the entry point's declared
     * {@code @Board} targets this build compiles for; it must name one of them. An entry point with no
     * {@code @Board} is unrestricted, so the requested board alone decides the target. It may be omitted
     * only when the entry point declares exactly one board —
     * declaring more than one and omitting it is a {@link CompileException}, since silently picking one
     * would make the build depend on declaration order.
     */
    public Program link(Map<String, JavaClass> classes, String mainClassName, Optional<String> requestedBoardId) {
        String internalName = mainClassName.replace('.', '/');
        JavaClass mainClass = classes.get(internalName);
        if (mainClass == null) {
            throw new CompileException("Main class not found on the classpath: " + mainClassName);
        }
        List<Board> declaredBoards = declaredBoards(mainClass, requestedBoardId);
        Board board = resolveBoard(declaredBoards, requestedBoardId);
        JavaMethod main = findMain(mainClass);
        MethodRef entryPoint = main.reference();
        JavaMethod mainInitializer = mainClass.findMethod("<clinit>", "()V");
        List<MethodRef> clojureFunctions = new ArrayList<>();
        ReachabilityClosure.Result reachability = reachabilityClosure.resolve(entryPoint,
                mainInitializer == null ? null : mainInitializer.reference(), classes,
                reference -> linkMethod(classes, reference, reference.equals(entryPoint)),
                (linked, work, calls, instantiated, lambdas) -> enqueueDependencies(linked, declaredBoards, classes,
                        work, calls, instantiated, lambdas, clojureFunctions), interfaceDispatchResolver,
                method -> hasReachableBody(method, classes),
                (dispatch, method) -> validateInterfaceTargetCapability(dispatch, method, declaredBoards));
        structuredTaskSupport.validateNoCustomJoiners(reachability.instantiatedClasses(),
                reachability.lambdaSites().values(), classes);
        for (Map.Entry<InterfaceCallSite, MethodRef> call : reachability.interfaceCalls().entrySet()) {
            if (!reachability.interfaceDispatches().containsKey(call.getKey())) {
                throw new CompileException(threadSupport.unresolvedMessage(call.getKey(), call.getValue()));
            }
        }
        if (mainClass.watchdogTimeoutMillis().isPresent()) {
            requireCapability(declaredBoards, Capability.WATCHDOG, "");
        }
        return new Program(entryPoint, reachability.methods(), classes, board,
                mainClass.watchdogTimeoutMillis(), reachability.interfaceDispatches(), reachability.lambdaSites(),
                clojureFunctions);
    }

    private void validateInterfaceTargetCapability(InterfaceDispatch dispatch, MethodRef method,
                                                   List<Board> declaredBoards) {
        intrinsicRegistry.resolve(method).map(REQUIRED_CAPABILITIES::get).ifPresent(capability ->
                requireCapability(declaredBoards, capability,
                        " (used through " + dispatch.interfaceMethod().displayName() + ")"));
    }

    /**
     * Every board the entry point's {@code @Board} annotation names. Without the annotation the program
     * is unrestricted and must receive its target from the requested board.
     */
    private List<Board> declaredBoards(JavaClass mainClass, Optional<String> requestedBoardId) {
        List<String> boardApiClassNames = mainClass.boardApiClassNames();
        if (boardApiClassNames.isEmpty()) {
            if (requestedBoardId.isEmpty()) {
                throw new CompileException("Target board is required when the entry point has no @Board annotation; "
                        + "pass -Djuno.board=<id> for the Maven plugin or --board=<id> for the standalone CLI");
            }
            return List.of(Board.fromId(requestedBoardId.get()));
        }
        return boardApiClassNames.stream().map(Board::fromApiClassName).distinct().toList();
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

    private String displayNames(List<Board> boards) {
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
        validateMethod(method, entryPoint, classes.keySet(), owner);
        List<Instruction> instructions = decoder.decode(method);
        ControlFlowGraph cfg = cfgBuilder.build(method.reference().displayName(), instructions,
                method.exceptionHandlers());
        return new LinkedMethod(owner, method, instructions, cfg);
    }

    /** Queues every method and static initializer {@code linked}'s invoke/static-field instructions reach. */
    private void enqueueDependencies(LinkedMethod linked, List<Board> declaredBoards, Map<String, JavaClass> classes,
            Deque<MethodRef> work, Map<InterfaceCallSite, MethodRef> interfaceCalls,
            Set<String> instantiatedClasses, Map<LambdaCallSite, LambdaSite> lambdaSites,
            List<MethodRef> clojureFunctions) {
        JavaClass owner = linked.owner();
        MethodRef caller = linked.method().reference();
        List<Instruction> instructions = linked.instructions();
        for (int index = 0; index < instructions.size(); index++) {
            Instruction instruction = instructions.get(index);
            int opcode = instruction.opcode();
            if (opcode == 182 || opcode == 183 || opcode == 184) {
                MethodRef called = owner.constantPool().methodRef(instruction.operandA());
                threadSupport.registerEntry(called, interfaceCalls);
                structuredTaskSupport.registerEntry(called, interfaceCalls);
                scopedValueSupport.registerEntry(called, interfaceCalls);
                bindClojureFunction(called, linked, index, classes, clojureFunctions, caller, declaredBoards, work);
                enqueueCall(called, caller, declaredBoards, classes, work);
            }
            if (opcode == 185) {
                MethodRef called = owner.constantPool().methodRef(instruction.operandA());
                structuredTaskSupport.registerEntry(called, interfaceCalls);
                interfaceCallRegistration.register(linked, instruction, called, classes, lambdaSites.values(),
                        interfaceCalls, interfaceDispatchResolver);
            }
            if (opcode == 186) {
                invokeDynamicSupport.register(linked, instruction, classes, instantiatedClasses, lambdaSites,
                        implementation -> enqueueCall(implementation, caller, declaredBoards, classes, work));
            }
            if (opcode == 187) {
                instantiatedClasses.add(owner.constantPool().className(instruction.operandA()));
            }
            if (opcode == 178 || opcode == 179) {
                enqueueStaticInitializer(owner.constantPool().fieldRef(instruction.operandA()), classes, work);
            }
            ClojureSupport.validateClassLiteral(linked, instruction);
        }
    }

    /** Records the Clojure function a namespace's {@code Var.internPrivate} binds, and queues its body. */
    private void bindClojureFunction(MethodRef called, LinkedMethod linked, int index,
            Map<String, JavaClass> classes, List<MethodRef> clojureFunctions, MethodRef caller,
            List<Board> declaredBoards, Deque<MethodRef> work) {
        ClojureSupport.boundFunction(called, linked, index, classes)
                .filter(function -> !clojureFunctions.contains(function))
                .ifPresent(function -> {
                    clojureFunctions.add(function);
                    enqueueCall(function, caller, declaredBoards, classes, work);
                });
    }

    private void enqueueCall(MethodRef called, MethodRef caller, List<Board> declaredBoards,
            Map<String, JavaClass> classes, Deque<MethodRef> work) {
        intrinsicRegistry.resolve(called).map(REQUIRED_CAPABILITIES::get).ifPresent(capability ->
                requireCapability(declaredBoards, capability, " (used from " + caller.displayName() + ")"));
        threadSupport.validateCall(called);
        lockSupport.validateCall(called);
        atomicSupport.validateCall(called);
        structuredTaskSupport.validateCall(called);
        scopedValueSupport.validateCall(called);
        bigNumberSupport.validateCall(called);
        uuidSupport.validateCall(called);
        localDateTimeSupport.validateCall(called);
        ClojureSupport.validateCall(called);
        if (called.equals(DRAW_TEXT_METHOD)) {
            work.addLast(DRAW_CHAR_METHOD);
        } else if (hasReachableBody(called, classes)) {
            work.addLast(called);
        }
    }

    /** Whether {@code called} is an ordinary method whose bytecode must be linked, not one Juno lowers itself. */
    private boolean hasReachableBody(MethodRef called, Map<String, JavaClass> classes) {
        return !intrinsicRegistry.isIntrinsic(called)
                && !ClojureSupport.isLowered(called)
                && !isRuntimeBaseConstructor(called)
                && !throwableTypes.isBuiltInConstructor(called)
                && !throwableTypes.isGetMessage(called, classes)
                && !throwableTypes.isAddSuppressed(called, classes)
                && !isEnumOperation(classes, called)
                && !isCompileTimeGetenv(called)
                && !supportedJdkMethods.isRequireNonNull(called);
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
        require(required, Capability.SECURE_ENTROPY, Intrinsic.UUID_RANDOM);
        require(required, Capability.LED_MATRIX, Intrinsic.LED_MATRIX_BEGIN, Intrinsic.LED_MATRIX_LOAD_FRAME,
                Intrinsic.LED_MATRIX_COLUMNS, Intrinsic.LED_MATRIX_CLEAR);
        require(required, Capability.WIFI, Intrinsic.WIFI_BEGIN, Intrinsic.WIFI_BEGIN_AP, Intrinsic.WIFI_STATUS, Intrinsic.WIFI_LOCAL_IP,
                Intrinsic.WIFI_GATEWAY_IP, Intrinsic.WIFI_MAC_ADDRESS, Intrinsic.UDP_LISTEN, Intrinsic.UDP_SEND, Intrinsic.UDP_BROADCAST, Intrinsic.UDP_RECEIVE,
                Intrinsic.UDP_STOP);
        require(required, Capability.HTTPS_CLIENT, Intrinsic.HTTPS_GET, Intrinsic.HTTPS_GET_PATH_BUFFER,
                Intrinsic.HTTPS_POST, Intrinsic.HTTPS_POST_BUFFER, Intrinsic.HTTPS_DELETE, Intrinsic.HTTPS_PATCH,
                Intrinsic.HTTPS_QUERY,
                Intrinsic.HTTPS_REQUEST_BUFFER);
        require(required, Capability.HTTPS_CLIENT, Intrinsic.LOCAL_DATE_TIME_NOW);
        // A URL-based request needs Wi-Fi whatever its scheme; whether it also needs plain HTTP or TLS is only known
        // from the URL literal, which the lowering checks against the board (HttpUrlCall).
        require(required, Capability.WIFI, Intrinsic.HTTP_URL_GET, Intrinsic.HTTP_URL_GET_PATH_BUFFER,
                Intrinsic.HTTP_URL_POST, Intrinsic.HTTP_URL_POST_BUFFER, Intrinsic.HTTP_URL_DELETE,
                Intrinsic.HTTP_URL_PATCH, Intrinsic.HTTP_URL_QUERY);
        require(required, Capability.TLS_EMAIL, Intrinsic.SMTP_SEND_TLS, Intrinsic.POP3_MESSAGE_COUNT,
                Intrinsic.POP3_READ_LATEST, Intrinsic.POP3_READ_SUBJECT);
        require(required, Capability.HTTP_CLIENT, Intrinsic.HTTP_GET, Intrinsic.HTTP_GET_PATH_BUFFER,
                Intrinsic.HTTP_POST, Intrinsic.HTTP_POST_BUFFER, Intrinsic.HTTP_DELETE, Intrinsic.HTTP_PATCH,
                Intrinsic.HTTP_QUERY);
        require(required, Capability.SMTP_STARTTLS, Intrinsic.SMTP_SEND);
        require(required, Capability.USB_HID, Intrinsic.MOUSE_BEGIN, Intrinsic.MOUSE_MOVE);
        require(required, Capability.STRUCTURED_CONCURRENCY, Intrinsic.TASK_JOINER_ALL_SUCCESSFUL,
                Intrinsic.TASK_JOINER_ANY_SUCCESSFUL, Intrinsic.TASK_JOINER_AWAIT_ALL_SUCCESSFUL,
                Intrinsic.TASK_JOINER_AWAIT_ALL, Intrinsic.TASK_SCOPE_OPEN_DEFAULT, Intrinsic.TASK_SCOPE_OPEN,
                Intrinsic.TASK_SCOPE_FORK_CALLABLE, Intrinsic.TASK_SCOPE_FORK_RUNNABLE, Intrinsic.TASK_SCOPE_JOIN,
                Intrinsic.TASK_SCOPE_IS_CANCELLED, Intrinsic.TASK_GET, Intrinsic.TASK_STATE, Intrinsic.TASK_EXCEPTION,
                Intrinsic.TASK_SCOPE_CLOSE, Intrinsic.SCOPED_VALUE_NEW, Intrinsic.SCOPED_VALUE_WHERE,
                Intrinsic.SCOPED_CARRIER_WHERE, Intrinsic.SCOPED_VALUE_GET, Intrinsic.SCOPED_VALUE_IS_BOUND,
                Intrinsic.SCOPED_VALUE_OR_ELSE, Intrinsic.SCOPED_CARRIER_RUN, Intrinsic.SCOPED_CARRIER_CALL,
                Intrinsic.MONITOR_ENTER, Intrinsic.MONITOR_EXIT, Intrinsic.REENTRANT_LOCK_NEW,
                Intrinsic.REENTRANT_LOCK_LOCK, Intrinsic.REENTRANT_LOCK_TRY_LOCK, Intrinsic.REENTRANT_LOCK_UNLOCK);
        require(required, Capability.BIG_NUMBERS, Arrays.stream(Intrinsic.values())
                .filter(new BigNumberMethods()::isBigNumber).toArray(Intrinsic[]::new));
        require(required, Capability.DOUBLE_MATH, Intrinsic.MATH_SIN, Intrinsic.MATH_COS, Intrinsic.MATH_TAN,
                Intrinsic.MATH_ASIN, Intrinsic.MATH_ACOS, Intrinsic.MATH_ATAN, Intrinsic.MATH_ATAN2, Intrinsic.MATH_EXP,
                Intrinsic.MATH_LOG, Intrinsic.MATH_LOG10, Intrinsic.MATH_POW, Intrinsic.MATH_CBRT, Intrinsic.MATH_HYPOT);
        require(required, Capability.HTTP_SERVER, Intrinsic.HTTP_SERVER_BEGIN, Intrinsic.HTTP_SERVER_ACCEPT,
                Intrinsic.HTTP_SERVER_METHOD, Intrinsic.HTTP_SERVER_PATH, Intrinsic.HTTP_SERVER_RESPOND,
                Intrinsic.HTTP_SERVER_RESPOND_BUILDER);
        require(required, Capability.BLUETOOTH_LE, Intrinsic.LEGO_HUB_CONNECT, Intrinsic.LEGO_HUB_IS_CONNECTED,
                Intrinsic.LEGO_HUB_TYPE, Intrinsic.LEGO_HUB_SET_MOTOR_POWER, Intrinsic.LEGO_HUB_BRAKE_MOTOR, Intrinsic.LEGO_HUB_HOLD_MOTOR,
                Intrinsic.LEGO_HUB_SET_LED_COLOR, Intrinsic.LEGO_HUB_ENABLE_SENSOR, Intrinsic.LEGO_HUB_READ_SENSOR,
                Intrinsic.LEGO_HUB_DISCONNECT, Intrinsic.LEGO_HUB_SWITCH_OFF, Intrinsic.LEGO_HUB_BATTERY_PERCENT,
                Intrinsic.LEGO_HUB_BUTTON_PRESSED, Intrinsic.LEGO_HUB_RSSI, Intrinsic.LEGO_HUB_FIRMWARE_VERSION,
                Intrinsic.LEGO_HUB_HARDWARE_VERSION, Intrinsic.LEGO_HUB_PORT_DEVICE, Intrinsic.LEGO_HUB_NAME, Intrinsic.LEGO_HUB_READ_SENSOR_VALUE,
                Intrinsic.LEGO_HUB_SENSOR_REPORT_SIZE, Intrinsic.LEGO_HUB_SENSOR_REPORT_COUNT, Intrinsic.LEGO_HUB_SET_LED_RGB, Intrinsic.LEGO_HUB_LINK_MOTORS,
                Intrinsic.LEGO_HUB_UNLINK_MOTORS, Intrinsic.LEGO_HUB_SET_LINKED_MOTOR_POWER,
                Intrinsic.LEGO_HUB_BRAKE_LINKED_MOTORS);
        return Collections.unmodifiableMap(required);
    }

    private static void require(Map<Intrinsic, Capability> required, Capability capability, Intrinsic... intrinsics) {
        for (Intrinsic intrinsic : intrinsics) {
            required.put(intrinsic, capability);
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
    private JavaMethod findMain(JavaClass mainClass) {
        JavaMethod conventional = mainClass.findMethod("main", "([Ljava/lang/String;)V");
        JavaMethod embedded = mainClass.findMethod("main", "()V");
        JavaMethod result = conventional != null ? conventional : embedded;
        if (result == null || !result.isStatic()) {
            throw new CompileException("Main class must declare static void main(String[]) or static void main()");
        }
        return result;
    }

    private void validateMethod(JavaMethod method, boolean entryPoint, Set<String> referenceClassNames,
            JavaClass owner) {
        if (method.isNative() || method.code() == null) {
            throw new CompileException("Native method has no Juno intrinsic: " + method.reference().displayName());
        }
        lockSupport.validateMethod(method);
        Descriptor descriptor = Descriptor.parse(method.descriptor());
        boolean conventionalMain = entryPoint
                && descriptor.parameters().equals(List.of("[Ljava/lang/String;"))
                && descriptor.returnsVoid();
        if (!conventionalMain && !descriptor.usesOnlyV01Types(referenceClassNames)
                && !ClojureSupport.acceptsFunctionDescriptor(owner.superClassName(), descriptor)
                && !ClojureSupport.acceptsDeftypeConstructor(owner, method, referenceClassNames)) {
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
