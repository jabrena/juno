package io.github.jabrena.juno.linker;

import java.util.List;

/** A validated {@code StringConcatFactory.makeConcatWithConstants} call site. */
public record StringConcatSite(List<String> argumentTypes, List<Part> parts) {
    public StringConcatSite {
        argumentTypes = List.copyOf(argumentTypes);
        parts = List.copyOf(parts);
    }

    /** One literal fragment or dynamic argument in the bootstrap recipe. */
    public sealed interface Part permits LiteralPart, ArgumentPart {
    }

    public record LiteralPart(String value) implements Part {
    }

    public record ArgumentPart(int index, String type) implements Part {
    }
}
