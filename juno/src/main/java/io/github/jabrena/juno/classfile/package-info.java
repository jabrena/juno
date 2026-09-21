/**
 * Reads standard JVM class files and resolves their constant-pool references, independent of
 * Juno's own bytecode subset or IR.
 *
 * <p>{@link io.github.jabrena.juno.classfile.ClassPath} loads class files from directories,
 * individual {@code .class} files, and JARs. {@link io.github.jabrena.juno.classfile.ClassFileReader}
 * parses one class file's bytes, resolving its
 * {@link io.github.jabrena.juno.classfile.ConstantPool} entries into a
 * {@link io.github.jabrena.juno.classfile.JavaClass} — its fields
 * ({@link io.github.jabrena.juno.classfile.FieldInfo}) and methods
 * ({@link io.github.jabrena.juno.classfile.JavaMethod}), each carrying raw bytecode for
 * {@code bytecode}/{@code lowering} to decode separately.
 * {@link io.github.jabrena.juno.classfile.MethodRef} and
 * {@link io.github.jabrena.juno.classfile.FieldRef} are the small, comparable
 * owner/name/descriptor keys used throughout the compiler to identify a method or field without
 * holding onto its full declaration.
 *
 * <p>This package only understands the class file format itself; it has no notion of which
 * bytecode instructions or class shapes Juno's v0.1 subset actually supports — that judgment
 * belongs to {@code linker} and {@code lowering}.
 */
package io.github.jabrena.juno.classfile;
