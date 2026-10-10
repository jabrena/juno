package io.github.jabrena.juno.classfile;

/** A resolved CONSTANT_InvokeDynamic call-site name/type plus its bootstrap-table index. */
public record InvokeDynamicRef(int bootstrapMethodIndex, String name, String descriptor) {
}
