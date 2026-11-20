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
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Performs closed-world reachability and resolves every static call before code generation. */
public final class Linker {

    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();
    private final CapabilityRequirements capabilityRequirements = new CapabilityRequirements();
    private final Esp32CamBusConflicts esp32CamBusConflicts = new Esp32CamBusConflicts();

    private final SupportedJdkMethods supportedJdkMethods = new SupportedJdkMethods();

    private final InterfaceCallRegistration interfaceCallRegistration = new InterfaceCallRegistration();

    private final ThreadSupport threadSupport = new ThreadSupport();

    private final AtomicSupport atomicSupport = new AtomicSupport();
    private final ConcurrencySupport concurrencySupport = new ConcurrencySupport();

    private final LockSupport lockSupport = new LockSupport();

    private final ScopedValueSupport scopedValueSupport = new ScopedValueSupport();

    private final BigNumberSupport bigNumberSupport = new BigNumberSupport();

    private final UuidSupport uuidSupport = new UuidSupport();
    private final DatagramSupport datagramSupport = new DatagramSupport();
    private final SocketSupport socketSupport = new SocketSupport();

    private final LocalDateTimeSupport localDateTimeSupport = new LocalDateTimeSupport();

    private final ThrowableTypes throwableTypes = new ThrowableTypes();

    private final StructuredTaskSupport structuredTaskSupport = new StructuredTaskSupport();

    private final InheritanceSupport inheritanceSupport = new InheritanceSupport();

    private final ReachableBodySupport reachableBodySupport = new ReachableBodySupport(intrinsicRegistry,
            supportedJdkMethods, throwableTypes);

    private final BytecodeDecoder decoder = new BytecodeDecoder();
    private final ControlFlowGraphBuilder cfgBuilder = new ControlFlowGraphBuilder();
    private final InterfaceDispatchResolver interfaceDispatchResolver = new InterfaceDispatchResolver();
    private final ReachabilityClosure reachabilityClosure = new ReachabilityClosure();

    private final InvokeDynamicSupport invokeDynamicSupport = new InvokeDynamicSupport();

    private final HttpLinkScan httpLinkScan = new HttpLinkScan();
    private final HttpQueryFolding httpQueryFolding = new HttpQueryFolding();

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
        DeclaredBoards declaredBoards = DeclaredBoards.of(mainClass, requestedBoardId);
        Board board = declaredBoards.target(requestedBoardId);
        JavaMethod main = findMain(mainClass);
        MethodRef entryPoint = main.reference();
        JavaMethod mainInitializer = mainClass.findMethod("<clinit>", "()V");
        List<MethodRef> clojureFunctions = new ArrayList<>();
        ReachabilityClosure.Result reachability = linkFor(classes, entryPoint, mainInitializer, board,
                clojureFunctions);
        structuredTaskSupport.validateNoCustomJoiners(reachability.instantiatedClasses(),
                reachability.lambdaSites().values(), classes);
        for (Map.Entry<InterfaceCallSite, MethodRef> call : reachability.interfaceCalls().entrySet()) {
            if (!reachability.interfaceDispatches().containsKey(call.getKey())) {
                throw new CompileException(threadSupport.unresolvedMessage(call.getKey(), call.getValue()));
            }
        }
        // Every other declared board is linked too, only to check it against the code it reaches itself: a call
        // behind a board query that folds away for that board needs no capability from it.
        for (Board other : declaredBoards.others(board)) {
            linkFor(classes, entryPoint, mainInitializer, other, new ArrayList<>());
        }
        if (mainClass.watchdogTimeoutMillis().isPresent()) {
            requireCapability(declaredBoards.boards(), Capability.WATCHDOG, "");
        }
        esp32CamBusConflicts.validate(board, reachability.methods());
        return new Program(entryPoint, reachability.methods(), classes, board,
                mainClass.watchdogTimeoutMillis(), reachability.interfaceDispatches(), reachability.lambdaSites(),
                clojureFunctions);
    }

    /**
     * Links the program as {@code board} sees it, with its board queries folded for that board, and checks every
     * capability the result reaches against {@code board} alone.
     */
    private ReachabilityClosure.Result linkFor(Map<String, JavaClass> classes, MethodRef entryPoint,
            @Nullable JavaMethod mainInitializer, Board board, List<MethodRef> clojureFunctions) {
        // Linked once, or twice when the program sends HTTP requests, so only what it uses is linked.
        ReachabilityClosure.Result reachability = httpLinkScan.link(httpFacts -> {
            clojureFunctions.clear();
            return resolveReachability(classes, entryPoint, mainInitializer, board, httpFacts, clojureFunctions);
        });
        if (inheritanceSupport.usesClassInheritance(reachability, classes)) {
            requireCapability(List.of(board), Capability.CLASS_INHERITANCE, "");
        }
        return reachability;
    }

    /** Walks every method reachable from the entry point, deciding HTTP link-time queries with {@code httpFacts}. */
    private ReachabilityClosure.Result resolveReachability(Map<String, JavaClass> classes, MethodRef entryPoint,
            @Nullable JavaMethod mainInitializer, Board board, HttpLinkFacts httpFacts,
            List<MethodRef> clojureFunctions) {
        List<Board> checkedBoards = List.of(board);
        return reachabilityClosure.resolve(entryPoint,
                mainInitializer == null ? null : mainInitializer.reference(), classes,
                reference -> linkMethod(classes, reference, reference.equals(entryPoint), board, httpFacts),
                (linked, work, calls, instantiated, lambdas) -> enqueueDependencies(linked, checkedBoards, classes,
                        work, calls, instantiated, lambdas, clojureFunctions), interfaceDispatchResolver,
                method -> reachableBodySupport.hasReachableBody(method, classes),
                (dispatch, method) -> validateInterfaceTargetCapability(dispatch, method, checkedBoards));
    }

    private void validateInterfaceTargetCapability(InterfaceDispatch dispatch, MethodRef method,
                                                   List<Board> declaredBoards) {
        intrinsicRegistry.resolve(method).flatMap(capabilityRequirements::forIntrinsic).ifPresent(capability ->
                requireCapability(declaredBoards, capability,
                        " (used through " + dispatch.interfaceMethod().displayName() + ")"));
    }

    /**
     * Decodes one reachable method. A branch on a board query is resolved against {@code board} first (see
     * {@link BoardQueryFolding}), so the side this target can never take is neither linked nor lowered.
     */
    private LinkedMethod linkMethod(Map<String, JavaClass> classes, MethodRef reference, boolean entryPoint,
            Board board, HttpLinkFacts httpFacts) {
        JavaClass owner = classes.get(reference.owner());
        if (owner == null) {
            throw new CompileException("Reachable class not found: " + reference.owner().replace('/', '.'));
        }
        JavaMethod method = owner.findMethod(reference.name(), reference.descriptor());
        if (method == null) {
            throw new CompileException("Reachable method not found: " + reference.displayName());
        }
        validateMethod(method, entryPoint, classes.keySet(), owner);
        BoardQueryFolding boardQueryFolding = new BoardQueryFolding(classes, decoder);
        BoardQueryFolding.Folded folded = httpQueryFolding.foldAfter(
                boardQueryFolding.fold(decoder.decode(method), owner, board), owner, httpFacts);
        List<Instruction> instructions = folded.instructions();
        ControlFlowGraph cfg = cfgBuilder.build(method.reference().displayName(), instructions,
                method.exceptionHandlers());
        if (folded.changed()) {
            cfg = boardQueryFolding.prune(cfg, method.exceptionHandlers());
            instructions = boardQueryFolding.instructions(cfg);
        }
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
            if (opcode == 182) {
                MethodRef called = owner.constantPool().methodRef(instruction.operandA());
                threadSupport.registerEntry(called, interfaceCalls);
                structuredTaskSupport.registerEntry(called, interfaceCalls);
                scopedValueSupport.registerEntry(called, interfaceCalls);
                bindClojureFunction(called, linked, index, classes, clojureFunctions, caller, declaredBoards, work);
                JavaClass calledOwner = classes.get(called.owner());
                boolean compilerHandled = calledOwner != null && (calledOwner.isEnum()
                        || throwableTypes.isGetMessage(called, classes)
                        || throwableTypes.isAddSuppressed(called, classes));
                if (calledOwner != null && !compilerHandled && !intrinsicRegistry.isIntrinsic(called)
                        && !ClojureSupport.isLowered(called)) {
                    interfaceCallRegistration.registerVirtual(linked, instruction, called, classes,
                            interfaceCalls, interfaceDispatchResolver);
                } else {
                    enqueueCall(called, caller, declaredBoards, classes, work);
                }
            } else if (opcode == 183 || opcode == 184) {
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
        intrinsicRegistry.resolve(called).flatMap(capabilityRequirements::forIntrinsic)
                .or(() -> capabilityRequirements.forMethod(called))
                .ifPresent(capability ->
                        requireCapability(declaredBoards, capability, " (used from " + caller.displayName() + ")"));
        threadSupport.validateCall(called);
        lockSupport.validateCall(called);
        atomicSupport.validateCall(called);
        concurrencySupport.validateCall(called);
        structuredTaskSupport.validateCall(called);
        scopedValueSupport.validateCall(called);
        bigNumberSupport.validateCall(called);
        uuidSupport.validateCall(called);
        datagramSupport.validateCall(called);
        socketSupport.validateCall(called);
        localDateTimeSupport.validateCall(called);
        ClojureSupport.validateCall(called);
        if (reachableBodySupport.hasReachableBody(called, classes)) {
            work.addLast(called);
        }
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
     * Each of {@code boards} must provide {@code capability}. {@link #link} checks every declared board, not just the
     * one this build targets, each against the code it reaches itself, so a portable program never compiles for one
     * of its boards and fails on another.
     */
    private void requireCapability(List<Board> declaredBoards, Capability capability, String context) {
        declaredBoards.stream().filter(board -> !board.supports(capability)).findFirst().ifPresent(board -> {
            throw new CompileException(capability.apiName() + " requires @Board("
                    + Arrays.stream(Board.values()).filter(candidate -> candidate.supports(capability))
                            .map(Board::annotationArgument).collect(Collectors.joining(" or "))
                    + "): " + capability.unsupportedReason(board) + context);
        });
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
        boolean generatedRecordEquals = owner.isRecord() && method.name().equals("equals")
                && method.descriptor().equals("(Ljava/lang/Object;)Z");
        if (!conventionalMain && !generatedRecordEquals
                && !BoardJdkClasses.acceptsMethod(owner, descriptor, referenceClassNames)
                && !descriptor.usesOnlyV01Types(referenceClassNames)
                && !ClojureSupport.acceptsFunctionDescriptor(owner.superClassName(), descriptor)
                && !ClojureSupport.acceptsDeftypeConstructor(owner, method, referenceClassNames)) {
            throw new CompileException("Juno methods may use only supported scalar, array, or closed-world reference parameters and returns: "
                    + method.reference().displayName());
        }
    }

}
