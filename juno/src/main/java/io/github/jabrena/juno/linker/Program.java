package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;
import java.util.Map;

/**
 * {@code classes} is every class loaded from the classpath (not just reachable methods' owners) — needed so
 * that lowering can resolve a {@code getstatic} on an enum constant field to its ordinal, since the field's
 * owner class may differ from the reachable method's own owner and is otherwise never visited (its
 * {@code <clinit>}/{@code <init>}/{@code values()}/etc. are deliberately never added to {@code methods}).
 */
public record Program(MethodRef entryPoint, List<LinkedMethod> methods, Map<String, JavaClass> classes) {
}
