package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;
import io.github.jabrena.juno.ir.InterfaceTarget;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.Descriptor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Pops a call's arguments (and receiver) and emits the call: an intrinsic, a direct call, or a dispatch over an
 * interface's implementations. The building block every invoke lowering shares.
 */
final class CallLowering {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    private final MethodContext context;
    private final OperandStack stack;
    private final HttpUrlCall httpUrlCall;

    CallLowering(MethodContext context) {
        this.context = context;
        this.stack = context.stack();
        this.httpUrlCall = new HttpUrlCall(context);
    }

    /**
     * Pops the arguments of {@code called}, last first. A string argument {@code intrinsic} requires (or prefers)
     * as compile-time text becomes a {@link CallArguments.Literal}.
     */
    CallArguments popArguments(Instruction instruction, MethodRef called, Descriptor descriptor,
                               Optional<Intrinsic> intrinsic) {
        CallArguments.Argument[] arguments = new CallArguments.Argument[descriptor.parameters().size()];
        for (int index = arguments.length - 1; index >= 0; index--) {
            String type = descriptor.parameters().get(index);
            arguments[index] = Descriptor.isString(type)
                    ? stringArgument(instruction, called, intrinsic, index)
                    : new CallArguments.RuntimeValue(stack.pop(typeOf(type)));
        }
        return new CallArguments(Arrays.asList(arguments));
    }

    /** Lowers a call to {@code called}, popping a receiver when {@code hasReceiver}, and pushes its result. */
    void call(Instruction instruction, MethodRef called, boolean hasReceiver, List<InterfaceTarget> interfaceTargets) {
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        Optional<Intrinsic> intrinsic = intrinsicRegistry.resolve(called);
        CallArguments arguments = popArguments(instruction, called, descriptor, intrinsic);
        Optional<Value> receiver = hasReceiver ? Optional.of(stack.pop()) : Optional.empty();
        Optional<Value> result = Optional.ofNullable(result(descriptor));
        if (intrinsic.isPresent() && interfaceTargets.isEmpty() && HttpUrlCall.handles(intrinsic.get())) {
            httpUrlCall.emit(instruction, called, intrinsic.get(), arguments, result);
        } else if (intrinsic.isPresent() && interfaceTargets.isEmpty()) {
            // A port operation never reads its stateless port, so the loads that produced it can become dead.
            Optional<Value> used = intrinsic.get().ignoresReceiver() ? Optional.empty() : receiver;
            context.emit(new IrInstruction.IntrinsicCall(result, intrinsic.get(), used, arguments.values(),
                    arguments.literals()));
        } else {
            List<Value> callArguments = new ArrayList<>();
            receiver.ifPresent(callArguments::add);
            callArguments.addAll(arguments.values());
            context.emit(interfaceTargets.isEmpty()
                    ? new IrInstruction.Call(result, called, List.copyOf(callArguments))
                    : new IrInstruction.InterfaceCall(result, List.copyOf(callArguments), interfaceTargets));
        }
        result.ifPresent(stack::push);
    }

    /** A fresh value for what {@code descriptor} returns, or {@code null} for a {@code void} method. */
    @Nullable Value result(Descriptor descriptor) {
        return descriptor.returnsVoid() ? null : context.values().of(typeOf(descriptor.returnType()));
    }

    /** The IR type of a parameter or return type: references are 32-bit words like {@code int}. */
    JunoType typeOf(String type) {
        if (Descriptor.isLong(type)) {
            return JunoType.INT64;
        }
        if (Descriptor.isDouble(type)) {
            return JunoType.FLOAT64;
        }
        return Descriptor.isFloat(type) ? JunoType.FLOAT32 : JunoType.INT32;
    }

    private CallArguments.Argument stringArgument(Instruction instruction, MethodRef called,
                                                  Optional<Intrinsic> intrinsic, int index) {
        Value value = stack.pop();
        String literal = context.facts().knownString(value);
        if (intrinsic.isPresent() && intrinsicRegistry.requiresLiteralStringArgument(intrinsic.get(), index)) {
            if (literal == null) {
                throw new CompileException(context.at(instruction) + ": " + called.displayName()
                        + " requires a compile-time string literal argument");
            }
            return new CallArguments.Literal(literal);
        }
        if (intrinsic.isPresent() && intrinsicRegistry.prefersLiteralStringArgument(intrinsic.get(), index)
                && literal != null) {
            return new CallArguments.Literal(literal);
        }
        return new CallArguments.RuntimeValue(value);
    }
}
