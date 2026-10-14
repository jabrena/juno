package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the C++ thunks AVR code calls the runtime shim through. avr-gcc passes a {@code const char*} in two
 * registers, an {@code int32_t} in four and a {@code uint8_t} in one, so assembly that only knows Java words cannot
 * call a C function directly without knowing its parameter types. Instead the generated code stores its argument
 * words in {@code juno_avr_args} (AAPCS layout: one word each, a 64-bit value as two starting at an even word) and
 * calls {@code juno_avr_thunk_<function>()}; the thunk, generated here from the function's own {@code extern "C"}
 * prototype in the shim source, converts each word to its declared type, calls the function, and returns the result
 * as an {@code int32_t} (r22-r25) or {@code uint64_t} (r18-r25). A pointer result is sign-extended from its 16 bits,
 * matching the shim's {@code intptr_t} casts and the backend's address loads.
 *
 * <p>A function with no prototype in the shim, or one with a C {@code double} parameter or result (avr-gcc's
 * {@code double} is 32 bits wide; the AVR shim uses {@code juno_f64} for Java's), is a compiler bug and fails the
 * compilation instead of producing a thunk that would pass the wrong bits.
 */
public final class AvrShimThunks {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    private static final String PREFIX = "juno_avr_thunk_";

    /** How one parameter or result crosses the word interface. */
    private enum Kind {
        VOID, WORD, POINTER, WIDE, FLOAT
    }

    private record Parameter(String type, Kind kind) {
    }


    public String thunkName(String function) {
        return PREFIX + function;
    }

    /** The helpers thunks read {@code juno_avr_args} with; emitted ahead of everything else in the shim. */
    String prelude() {
        return runtimeSource.load("avr/shim-thunks.cpp");
    }

    /** One thunk per function in {@code functions}, each built from its prototype in {@code shim}. */
    public String generate(String shim, Set<String> functions) {
        StringBuilder thunks = new StringBuilder("\n// Word-interface thunks the generated assembly calls (see AvrShimThunks).\n");
        for (String function : functions) {
            thunks.append(thunk(shim, function));
        }
        return thunks.toString();
    }

    private String thunk(String shim, String function) {
        Matcher matcher = Pattern.compile("extern\\s+\"C\"\\s+([\\[\\]\\w\\s*]*?)\\b" + Pattern.quote(function)
                + "\\s*\\(([^)]*)\\)\\s*\\{").matcher(shim);
        if (!matcher.find()) {
            throw new CompileException("Internal error: the AVR runtime shim has no extern \"C\" definition of "
                    + function);
        }
        String returnType = matcher.group(1).replace("[[noreturn]]", "").replace("JUNO_ASM_ABI", "").strip();
        Kind result = kind(function, returnType);
        List<String> arguments = new ArrayList<>();
        int word = 0;
        for (Parameter parameter : parameters(function, matcher.group(2))) {
            switch (parameter.kind()) {
                case WIDE -> {
                    word += word % 2;
                    arguments.add("static_cast<" + parameter.type() + ">(juno_avr_arg64(" + word + "))");
                    word += 2;
                }
                case POINTER -> arguments.add("reinterpret_cast<" + parameter.type()
                        + ">(static_cast<uintptr_t>(juno_avr_args[" + word++ + "]))");
                case FLOAT -> arguments.add("juno_avr_arg_float(" + word++ + ")");
                case WORD -> arguments.add("static_cast<" + parameter.type() + ">(juno_avr_args[" + word++ + "])");
                case VOID -> throw new IllegalStateException("void parameter");
            }
        }
        String call = function + "(" + String.join(", ", arguments) + ")";
        return switch (result) {
            case VOID -> "extern \"C\" void " + thunkName(function) + "() {\n  " + call + ";\n}\n";
            case WORD -> "extern \"C\" int32_t " + thunkName(function) + "() {\n  return static_cast<int32_t>("
                    + call + ");\n}\n";
            case POINTER -> "extern \"C\" int32_t " + thunkName(function)
                    + "() {\n  return static_cast<int32_t>(reinterpret_cast<intptr_t>(" + call + "));\n}\n";
            case FLOAT -> "extern \"C\" int32_t " + thunkName(function) + "() {\n  return juno_avr_float_bits("
                    + call + ");\n}\n";
            case WIDE -> "extern \"C\" uint64_t " + thunkName(function) + "() {\n  return static_cast<uint64_t>("
                    + call + ");\n}\n";
        };
    }

    private List<Parameter> parameters(String function, String text) {
        List<Parameter> parameters = new ArrayList<>();
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.equals("void")) {
            return parameters;
        }
        // A single literal comma, never a regex, so the plain split is exact here.
        @SuppressWarnings("StringSplitter")
        String[] declarations = trimmed.split(",");
        for (String declaration : declarations) {
            String parameter = declaration.strip();
            // The type is everything before the parameter's name.
            Matcher name = Pattern.compile("^(.*?)\\s*\\b\\w+$").matcher(parameter);
            String type = name.matches() && !name.group(1).isBlank() ? name.group(1).strip() : parameter;
            parameters.add(new Parameter(type, kind(function, type)));
        }
        return parameters;
    }

    private Kind kind(String function, String type) {
        String normalized = type.replaceAll("\\s+", " ").strip();
        if (normalized.contains("*")) {
            return Kind.POINTER;
        }
        return switch (normalized) {
            case "void" -> Kind.VOID;
            case "int64_t", "uint64_t", "juno_f64", "long long", "unsigned long long" -> Kind.WIDE;
            case "float" -> Kind.FLOAT;
            case "double" -> throw new CompileException("Internal error: " + function
                    + " takes or returns a C double, which avr-gcc makes 32 bits wide; use juno_f64");
            default -> Kind.WORD;
        };
    }
}
