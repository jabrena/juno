package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

/** Fully resolved {@code LambdaMetafactory.metafactory} call site. */
public record LambdaSite(LambdaCallSite callSite, String syntheticClassName, MethodRef interfaceMethod,
                         MethodHandleRef implementation, List<String> captureTypes,
                         String instantiatedMethodDescriptor) {
    public LambdaSite {
        captureTypes = List.copyOf(captureTypes);
    }

    public boolean isCapturing() {
        return !captureTypes.isEmpty();
    }
}
