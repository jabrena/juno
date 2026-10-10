package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;

/**
 * A cheap, purely structural check (name + zero-arg descriptor against a record's component list) used by
 * the linker's reachability scan to avoid rejecting a record accessor call as "not a Juno intrinsic" before
 * it ever reaches lowering. It deliberately does NOT validate that the accessor's own bytecode is the
 * trivial compiler-generated shape (just returning the field) — that deeper check happens once, lazily, in
 * {@link io.github.jabrena.juno.lowering.BytecodeToIr}, which is also why a record accessor call is never
 * enqueued as reachable here: its body must never be linked/decoded through the normal path.
 */
public final class RecordSupport {
    private RecordSupport() {
    }

    public static boolean isAccessorCall(Map<String, JavaClass> classes, MethodRef called) {
        JavaClass owner = classes.get(called.owner());
        if (owner == null || !owner.isRecord()) {
            return false;
        }
        Descriptor descriptor = Descriptor.parse(called.descriptor());
        return descriptor.parameters().isEmpty()
                && owner.recordComponents().stream().anyMatch(component -> component.name().equals(called.name()));
    }
}
