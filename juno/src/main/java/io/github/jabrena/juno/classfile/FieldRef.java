package io.github.jabrena.juno.classfile;

public record FieldRef(String owner, String name, String descriptor) {
    public String displayName() {
        return owner.replace('/', '.') + "." + name + ":" + descriptor;
    }
}
