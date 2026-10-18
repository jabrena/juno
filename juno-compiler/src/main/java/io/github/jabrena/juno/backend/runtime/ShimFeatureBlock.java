package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One optional shim helper block, appended to the generated C++ only when its feature is used. {@link RuntimeShim}
 * and {@link AvrRuntimeShim} each generate theirs from a long run of single-feature checks
 * ({@code if (uses(X)) shim.append(...)}); every one of those is independent of the others and order-preserving, so
 * collecting a run of them as an ordered list and appending through {@link #appendUsed} keeps every feature's text
 * and append order exactly as written, as one loop in place of one branch per feature.
 */
record ShimFeatureBlock(ShimFeature feature, Supplier<String> block) {
    static void appendUsed(StringBuilder shim, Set<ShimFeature> features, List<ShimFeatureBlock> blocks) {
        for (ShimFeatureBlock entry : blocks) {
            if (features.contains(entry.feature())) {
                shim.append(entry.block().get());
            }
        }
    }
}
