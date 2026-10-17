package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ClojureSupport;

import java.util.List;
import java.util.Optional;

/**
 * Lowers the {@link ClojureSupport} calls. A var is the handle of its function, so reading a var leaves the handle
 * on the stack, and {@code IFn.applyTo} is a direct call to the one function the program binds.
 */
final class ClojureLowering {
    private final MethodContext context;
    private final OperandStack stack;

    ClojureLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
    }

    void lower(Instruction instruction, MethodRef called) {
        if (called.equals(ClojureSupport.VAR_INTERN)) {
            internPrivate(instruction);
        } else if (called.equals(ClojureSupport.VAR_IS_BOUND)) {
            // Every var a program reaches is bound to a function; an unbound name has handle 0, which is never read.
            stack.pop();
            stack.pushInt(1);
        } else if (called.equals(ClojureSupport.VAR_GET) || called.equals(ClojureSupport.VAR_GET_RAW_ROOT)
                || called.equals(ClojureSupport.VAR_DEREF)) {
            // The receiver is the var's handle already, so it stays on the stack as the result.
        } else if (called.equals(ClojureSupport.RT_SEQ)) {
            // The entry point's arguments are always empty on a board, so the sequence is nil.
            stack.pop();
            stack.pushInt(0);
        } else if (called.equals(ClojureSupport.LOAD_WITH_CLASS)) {
            // Loading a namespace from its class is what Juno's linking already did, so nothing is pushed.
            stack.pop();
            stack.pop();
            stack.pushInt(0);
        } else if (called.equals(ClojureSupport.RT_FLOAT_CAST)) {
            new ConversionLowering(context).doubleToFloat();
        } else if (called.equals(ClojureSupport.RT_FLOAT_CAST_OF_LONG)) {
            new ConversionLowering(context).longToFloat();
        } else if (called.equals(ClojureSupport.RT_INT_CAST) || called.equals(ClojureSupport.RT_UNCHECKED_INT_CAST)) {
            new ConversionLowering(context).longToInt();
        } else if (called.equals(ClojureSupport.NUMBERS_INT_ARRAY)) {
            intArray(instruction);
        } else if (called.owner().equals("clojure/lang/Numbers")) {
            numbers(instruction, called);
        } else if (called.equals(ClojureSupport.LONG_VALUE_OF)) {
            boxLiteral(instruction);
        } else if (called.equals(ClojureSupport.RT_AGET)) {
            Value index = stack.pop();
            Value array = stack.pop();
            stack.push(new ArrayLowering(context).loadElement(ArrayElementType.INT, array, index));
        } else if (called.equals(ClojureSupport.RT_ASET)) {
            Value element = stack.pop();
            Value index = stack.pop();
            Value array = stack.pop();
            new ArrayLowering(context).storeElement(ArrayElementType.INT, array, index, element);
            stack.push(element);
        } else if (called.equals(ClojureSupport.APPLY_TO)) {
            applyTo(instruction);
        } else {
            throw new CompileException(context.at(instruction) + ": Clojure call not supported by Juno: "
                    + called.displayName());
        }
    }

    /** {@code Var.internPrivate(ns, name)}: the handle of the function the two literal names identify, or 0. */
    private void internPrivate(Instruction instruction) {
        Value name = stack.pop();
        Value namespace = stack.pop();
        String namespaceName = context.facts().knownString(namespace);
        String varName = context.facts().knownString(name);
        if (namespaceName == null || varName == null) {
            throw new CompileException(context.at(instruction)
                    + ": Var.internPrivate needs literal namespace and variable names");
        }
        String functionClass = ClojureSupport.functionClass(namespaceName, varName);
        List<MethodRef> functions = context.program().clojureFunctions();
        int handle = 0;
        for (int index = 0; index < functions.size(); index++) {
            if (functions.get(index).owner().equals(functionClass)) {
                handle = index + 1;
            }
        }
        stack.pushInt(handle);
    }

    /**
     * {@code Long.valueOf(x)}: a boxed long is one word here, so only a long literal that fits an int can be boxed; its
     * word is the int itself. Any other boxed long is rejected rather than truncated.
     */
    private void boxLiteral(Instruction instruction) {
        OperandStack.LongHalves value = stack.popHalves();
        Integer low = context.facts().knownInt(value.low());
        Integer high = context.facts().knownInt(value.high());
        if (low == null || high == null || high != low >> 31) {
            throw new CompileException(context.at(instruction)
                    + ": a Clojure boxed long is supported only for a literal that fits an int");
        }
        stack.pushInt(low);
    }

    /** {@code Numbers.int_array(size)}: {@code (int-array n)}, a new int array of a literal size. */
    private void intArray(Instruction instruction) {
        Value size = stack.pop();
        Integer length = context.facts().knownInt(size);
        if (length == null) {
            throw new CompileException(context.at(instruction) + ": (int-array n) needs a literal size");
        }
        stack.push(new ArrayLowering(context).allocate(ArrayElementType.INT, length));
    }

    /** {@code Numbers} arithmetic on primitive longs, through the same IR as the JVM's {@code LADD}/{@code LDIV}/... */
    private void numbers(Instruction instruction, MethodRef called) {
        ArithmeticLowering arithmetic = new ArithmeticLowering(context);
        switch (called.name()) {
            case "add" -> arithmetic.longBinary(BinaryOp.ADD);
            case "minus" -> {
                if (called.descriptor().equals("(J)J")) {
                    arithmetic.longNegate();
                } else {
                    arithmetic.longBinary(BinaryOp.SUBTRACT);
                }
            }
            case "multiply" -> arithmetic.longBinary(BinaryOp.MULTIPLY);
            case "quotient" -> arithmetic.longDivision(instruction, BinaryOp.DIVIDE);
            case "remainder" -> arithmetic.longDivision(instruction, BinaryOp.REMAINDER);
            case "inc" -> {
                stack.pushLong(1);
                arithmetic.longBinary(BinaryOp.ADD);
            }
            case "lt" -> new ConversionLowering(context).longComparison(Condition.LESS_THAN);
            case "lte" -> new ConversionLowering(context).longComparison(Condition.LESS_EQUAL);
            case "gt" -> new ConversionLowering(context).longComparison(Condition.GREATER_THAN);
            case "gte" -> new ConversionLowering(context).longComparison(Condition.GREATER_EQUAL);
            case "equiv" -> new ConversionLowering(context).longComparison(Condition.EQUAL);
            case "dec" -> {
                stack.pushLong(1);
                arithmetic.longBinary(BinaryOp.SUBTRACT);
            }
            default -> throw new CompileException(context.at(instruction) + ": Clojure call not supported by Juno: "
                    + called.displayName());
        }
    }

    /**
     * {@code IFn.applyTo(args)} on a var handle. Juno cannot pick the function from a handle at run time yet, so a
     * program may bind only one function; the call goes to it directly.
     */
    private void applyTo(Instruction instruction) {
        Value arguments = stack.pop();
        stack.pop();
        List<MethodRef> functions = context.program().clojureFunctions();
        if (functions.size() != 1) {
            throw new CompileException(context.at(instruction)
                    + ": calling a Clojure function through IFn needs exactly one bound function, found "
                    + functions.size());
        }
        Value result = context.values().int32();
        context.emit(new IrInstruction.Call(Optional.of(result), functions.getFirst(), List.of(arguments)));
        stack.push(result);
    }
}
