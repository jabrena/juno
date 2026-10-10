package io.github.jabrena.juno.classfile;

public record MethodRef(String owner, String name, String descriptor) {
    public String displayName() {
        return owner.replace('/', '.') + "." + name + descriptor;
    }
}
