package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Computes the fixed point of direct calls, allocated classes, and interface-dispatch targets. */
final class ReachabilityClosure {

    record Result(List<LinkedMethod> methods, Map<InterfaceCallSite, InterfaceDispatch> interfaceDispatches,
                  Map<InterfaceCallSite, MethodRef> interfaceCalls) {
    }

    @FunctionalInterface
    interface DependencyEnqueuer {
        void enqueue(LinkedMethod method, Deque<MethodRef> work, Map<InterfaceCallSite, MethodRef> interfaceCalls,
                     Set<String> instantiatedClasses);
    }

    Result resolve(MethodRef entryPoint, MethodRef mainInitializer, Map<String, JavaClass> classes,
                   Function<MethodRef, LinkedMethod> methodLinker, DependencyEnqueuer dependencyEnqueuer,
                   InterfaceDispatchResolver interfaceResolver, Predicate<MethodRef> hasReachableBody,
                   BiConsumer<InterfaceDispatch, MethodRef> interfaceTargetValidator) {
        Map<MethodRef, LinkedMethod> reachable = new LinkedHashMap<>();
        Map<InterfaceCallSite, MethodRef> interfaceCalls = new LinkedHashMap<>();
        Set<String> instantiatedClasses = new TreeSet<>();
        Deque<MethodRef> work = new ArrayDeque<>();
        if (mainInitializer != null) {
            work.add(mainInitializer);
        }
        work.add(entryPoint);

        Map<InterfaceCallSite, InterfaceDispatch> dispatches = Map.of();
        while (true) {
            linkPending(work, reachable, methodLinker, dependencyEnqueuer, interfaceCalls, instantiatedClasses);
            dispatches = interfaceResolver.resolve(interfaceCalls, instantiatedClasses, classes);
            enqueueInterfaceTargets(dispatches, reachable, work, hasReachableBody, interfaceTargetValidator);
            if (work.isEmpty()) {
                return new Result(List.copyOf(reachable.values()), dispatches, interfaceCalls);
            }
        }
    }

    private void linkPending(Deque<MethodRef> work, Map<MethodRef, LinkedMethod> reachable,
                             Function<MethodRef, LinkedMethod> methodLinker,
                             DependencyEnqueuer dependencyEnqueuer,
                             Map<InterfaceCallSite, MethodRef> interfaceCalls,
                             Set<String> instantiatedClasses) {
        while (!work.isEmpty()) {
            MethodRef reference = work.removeFirst();
            if (!reachable.containsKey(reference)) {
                LinkedMethod linked = methodLinker.apply(reference);
                reachable.put(reference, linked);
                dependencyEnqueuer.enqueue(linked, work, interfaceCalls, instantiatedClasses);
            }
        }
    }

    private void enqueueInterfaceTargets(Map<InterfaceCallSite, InterfaceDispatch> dispatches,
                                         Map<MethodRef, LinkedMethod> reachable, Deque<MethodRef> work,
                                         Predicate<MethodRef> hasReachableBody,
                                         BiConsumer<InterfaceDispatch, MethodRef> interfaceTargetValidator) {
        for (InterfaceDispatch dispatch : dispatches.values()) {
            for (InterfaceDispatch.Target target : dispatch.targets()) {
                MethodRef method = target.method();
                interfaceTargetValidator.accept(dispatch, method);
                if (hasReachableBody.test(method) && !reachable.containsKey(method)) {
                    work.addLast(method);
                }
            }
        }
    }
}
