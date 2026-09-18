package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record Descriptor(List<String> parameters, String returnType) {
    public static Descriptor parse(String descriptor) {
        if (descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            throw new CompileException("Invalid method descriptor: " + descriptor);
        }
        List<String> parameters = new ArrayList<>();
        int cursor = 1;
        while (cursor < descriptor.length() && descriptor.charAt(cursor) != ')') {
            ParsedType parsed = parseType(descriptor, cursor);
            parameters.add(parsed.type());
            cursor = parsed.next();
        }
        if (cursor >= descriptor.length() || descriptor.charAt(cursor) != ')') {
            throw new CompileException("Invalid method descriptor: " + descriptor);
        }
        ParsedType returnType = parseType(descriptor, cursor + 1);
        if (returnType.next() != descriptor.length()) {
            throw new CompileException("Invalid method descriptor: " + descriptor);
        }
        return new Descriptor(List.copyOf(parameters), returnType.type());
    }

    public boolean returnsVoid() {
        return returnType.equals("V");
    }

    /** Enum-free convenience overload, e.g. for callers that never touch enum-typed descriptors. */
    public boolean usesOnlyV01Types() {
        return usesOnlyV01Types(Set.of());
    }

    /**
     * {@code enumClassNames} is the internal (slash-separated) name of every class recognized as a simple
     * enum constant source (see {@link io.github.jabrena.juno.classfile.JavaClass#isEnum()}) — an
     * enum-typed parameter/return is accepted only if its class is in this set, since Juno represents an
     * enum constant purely as its ordinal {@code int} and never constructs a real object for it.
     */
    public boolean usesOnlyV01Types(Set<String> enumClassNames) {
        return parameters.stream().allMatch(type -> isSupportedParameterType(type, enumClassNames))
                && (returnsVoid() || isIntegerLike(returnType) || isLong(returnType) || isFloat(returnType)
                        || isDouble(returnType)
                        || isArrayType(returnType)
                        || isEnumType(returnType, enumClassNames));
    }

    public static boolean isIntegerLike(String type) {
        return type.length() == 1 && "ZBCSI".contains(type);
    }

    public static boolean isFloat(String type) {
        return type.equals("F");
    }

    public static boolean isLong(String type) {
        return type.equals("J");
    }

    public static boolean isDouble(String type) {
        return type.equals("D");
    }

    public static int jvmSlots(String type) {
        if (type.equals("V")) {
            return 0;
        }
        return type.equals("J") || type.equals("D") ? 2 : 1;
    }

    /**
     * Arrays are pointer-passed; there is no heap, so an array parameter/return is only ever a handle to
     * storage that outlives the call — see {@link io.github.jabrena.juno.lowering.BytecodeToIr} for the
     * (narrow, always-sound) rules on when returning one is actually accepted.
     */
    public static boolean isArrayType(String type) {
        return type.length() == 2 && type.charAt(0) == '[' && "ZBCSIJFD".indexOf(type.charAt(1)) >= 0;
    }

    /** The element type character of an array descriptor, e.g. {@code B} for {@code [B}. */
    public static char arrayElementDescriptor(String arrayType) {
        return arrayType.charAt(1);
    }

    /** Whether {@code type} is an object-type descriptor (e.g. {@code Lcom/foo/Direction;}) for a known enum. */
    public static boolean isEnumType(String type, Set<String> enumClassNames) {
        return type.length() > 2 && type.charAt(0) == 'L' && type.endsWith(";")
                && enumClassNames.contains(type.substring(1, type.length() - 1));
    }

    private static boolean isSupportedParameterType(String type, Set<String> enumClassNames) {
        return isIntegerLike(type) || isLong(type) || isFloat(type) || isDouble(type) || isArrayType(type)
                || isEnumType(type, enumClassNames);
    }

    private static ParsedType parseType(String descriptor, int start) {
        if (start >= descriptor.length()) {
            throw new CompileException("Invalid method descriptor: " + descriptor);
        }
        char type = descriptor.charAt(start);
        if ("VZBCSIJFD".indexOf(type) >= 0) {
            return new ParsedType(String.valueOf(type), start + 1);
        }
        if (type == 'L') {
            int end = descriptor.indexOf(';', start);
            if (end < 0) {
                throw new CompileException("Invalid method descriptor: " + descriptor);
            }
            return new ParsedType(descriptor.substring(start, end + 1), end + 1);
        }
        if (type == '[') {
            ParsedType component = parseType(descriptor, start + 1);
            return new ParsedType("[" + component.type(), component.next());
        }
        throw new CompileException("Invalid method descriptor: " + descriptor);
    }

    private record ParsedType(String type, int next) {
    }
}
