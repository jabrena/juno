package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;

/** The supported {@code java.util.UUID} methods. */
public final class UuidMethods {
    static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef("java/util/UUID", "<init>", "(JJ)V"), Intrinsic.UUID_NEW),
            Map.entry(new MethodRef("java/util/UUID", "randomUUID", "()Ljava/util/UUID;"), Intrinsic.UUID_RANDOM),
            Map.entry(new MethodRef("java/util/UUID", "getMostSignificantBits", "()J"),
                    Intrinsic.UUID_GET_MOST_SIGNIFICANT_BITS),
            Map.entry(new MethodRef("java/util/UUID", "getLeastSignificantBits", "()J"),
                    Intrinsic.UUID_GET_LEAST_SIGNIFICANT_BITS),
            Map.entry(new MethodRef("java/util/UUID", "version", "()I"), Intrinsic.UUID_VERSION),
            Map.entry(new MethodRef("java/util/UUID", "variant", "()I"), Intrinsic.UUID_VARIANT),
            Map.entry(new MethodRef("java/util/UUID", "equals", "(Ljava/lang/Object;)Z"), Intrinsic.UUID_EQUALS),
            Map.entry(new MethodRef("java/util/UUID", "compareTo", "(Ljava/util/UUID;)I"),
                    Intrinsic.UUID_COMPARE_TO),
            Map.entry(new MethodRef("java/util/UUID", "hashCode", "()I"), Intrinsic.UUID_HASH_CODE),
            Map.entry(new MethodRef("java/util/UUID", "fromString", "(Ljava/lang/String;)Ljava/util/UUID;"),
                    Intrinsic.UUID_FROM_STRING),
            Map.entry(new MethodRef("java/util/UUID", "toString", "()Ljava/lang/String;"),
                    Intrinsic.UUID_TO_STRING));

    private UuidMethods() {
    }
}
