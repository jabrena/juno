package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.linker.ConstantTables;
import io.github.jabrena.juno.linker.InterfaceCallSite;
import io.github.jabrena.juno.linker.InterfaceDispatch;
import io.github.jabrena.juno.linker.LambdaCallSite;
import io.github.jabrena.juno.linker.LambdaSite;
import io.github.jabrena.juno.linker.StringConcatResolver;

import java.util.List;
import java.util.Map;

/**
 * What every method's lowering needs to know about the whole program.
 *
 * @param classes the closed world, by internal class name
 * @param throwableClasses every throwable class the program allocates; a class's index is its runtime class id
 * @param interfaceDispatches the implementations each interface call site can reach
 * @param objectTypeIds the runtime type id of each class that takes part in polymorphic dispatch
 * @param lambdaSites the lambdas and method references, by {@code invokedynamic} call site
 * @param throwing the methods that may return with an exception pending
 * @param constantTables the flash-resident {@code static final} lookup tables
 * @param divisionByZeroUnwinds whether a division by zero outside a local handler raises an exception for callers
 * @param board the board the program is compiled for, which decides what a URL-based request may use
 */
record ProgramContext(Map<String, JavaClass> classes, List<String> throwableClasses,
                      Map<InterfaceCallSite, InterfaceDispatch> interfaceDispatches,
                      Map<String, Integer> objectTypeIds, Map<LambdaCallSite, LambdaSite> lambdaSites,
                      ThrowingMethods throwing, ConstantTables constantTables, boolean divisionByZeroUnwinds,
                      BytecodeDecoder decoder, StringConcatResolver stringConcatResolver, Board board) {

    /** This context once {@code throwing} is known. */
    ProgramContext withThrowing(ThrowingMethods throwing) {
        return new ProgramContext(classes, throwableClasses, interfaceDispatches, objectTypeIds, lambdaSites,
                throwing, constantTables, divisionByZeroUnwinds, decoder, stringConcatResolver, board);
    }
}
