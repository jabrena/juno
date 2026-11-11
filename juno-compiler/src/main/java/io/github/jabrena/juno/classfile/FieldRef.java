package io.github.jabrena.juno.classfile;

public record FieldRef(String owner, String name, String descriptor, boolean volatileField) {
    public FieldRef(String owner, String name, String descriptor) {
        this(owner, name, descriptor, false);
    }

    public String displayName() {
        return owner.replace('/', '.') + "." + name + ":" + descriptor;
    }
}
