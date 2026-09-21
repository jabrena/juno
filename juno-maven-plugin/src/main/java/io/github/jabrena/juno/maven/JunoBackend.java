package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompileException;

import java.util.Locale;

enum JunoBackend {
    ASM,
    CPP;

    static JunoBackend parse(String value) {
        if (value == null || value.isBlank()) {
            return ASM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CompileException("Unsupported Juno backend '" + value + "'; expected asm or cpp");
        }
    }
}
