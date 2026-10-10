package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.Map;

/**
 * Closed-world plumbing for the {@code java.lang.ScopedValue} subset (JDK 25, final API). A {@code ScopedValue}
 * is a small key id and a {@code Carrier} is a chain of arena-allocated bindings; {@code run}/{@code call}
 * enter the carrier in the runtime shim and invoke the program's {@code Runnable}/{@code CallableOp} through the
 * generated entry functions, which this class registers as synthetic interface-call sites.
 */
public final class ScopedValueSupport {
    public static final String SCOPED_VALUE = "java/lang/ScopedValue";
    public static final String CARRIER = SCOPED_VALUE + "$Carrier";
    public static final String CALLABLE_OP = SCOPED_VALUE + "$CallableOp";

    public static final MethodRef ENTRY_METHOD =
            new MethodRef(SCOPED_VALUE, "$junoCallEntry", "(L" + CALLABLE_OP + ";)Ljava/lang/Object;");
    public static final InterfaceCallSite ENTRY_SITE = new InterfaceCallSite(ENTRY_METHOD, 0);
    public static final MethodRef CALLABLE_OP_CALL = new MethodRef(CALLABLE_OP, "call", "()Ljava/lang/Object;");

    private static final MethodRef CARRIER_RUN = new MethodRef(CARRIER, "run", "(Ljava/lang/Runnable;)V");
    private static final MethodRef CARRIER_CALL =
            new MethodRef(CARRIER, "call", "(L" + CALLABLE_OP + ";)Ljava/lang/Object;");

    private ScopedValueSupport() {
    }

    /** Registers the synthetic {@code Runnable.run()} / {@code CallableOp.call()} site a carrier call needs. */
    public static void registerEntry(MethodRef called, Map<InterfaceCallSite, MethodRef> interfaceCalls) {
        if (called.equals(CARRIER_RUN)) {
            interfaceCalls.put(ThreadSupport.ENTRY_SITE, ThreadSupport.RUNNABLE_RUN);
        } else if (called.equals(CARRIER_CALL)) {
            interfaceCalls.put(ENTRY_SITE, CALLABLE_OP_CALL);
        }
    }

    public static boolean isEntrySite(InterfaceCallSite site) {
        return site.equals(ENTRY_SITE);
    }

    public static boolean isCallableOpCall(MethodRef called) {
        return called.equals(CALLABLE_OP_CALL);
    }

    /** Whether {@code descriptor} is a {@code ScopedValue} or {@code Carrier}: one word, like a thread handle. */
    public static boolean isScopedValueType(String descriptor) {
        return descriptor.equals("L" + SCOPED_VALUE + ";") || descriptor.equals("L" + CARRIER + ";");
    }

    public static boolean isScopedValueOwner(String owner) {
        return owner.equals(SCOPED_VALUE) || owner.equals(CARRIER) || owner.equals(CALLABLE_OP);
    }

    /** Rejects API calls outside the deliberately small subset with a useful diagnostic. */
    public static void validateCall(MethodRef called) {
        if (isScopedValueOwner(called.owner()) && !IntrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's ScopedValue subset supports only ScopedValue.newInstance(), "
                    + "ScopedValue.where(key, value), Carrier.where(key, value), Carrier.run(Runnable), "
                    + "Carrier.call(CallableOp), get(), isBound(), and orElse(value); the other ScopedValue "
                    + "methods (runWhere, callWhere, getWhere, orElseThrow, Carrier.get) are unsupported: "
                    + called.displayName());
        }
    }
}
