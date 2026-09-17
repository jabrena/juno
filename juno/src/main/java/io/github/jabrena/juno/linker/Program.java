package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.List;

public record Program(MethodRef entryPoint, List<LinkedMethod> methods) {
}
