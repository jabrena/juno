package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;

import java.util.ArrayList;
import java.util.List;

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

    public boolean usesOnlyV01Types() {
        return parameters.stream().allMatch(Descriptor::isSupportedParameterType)
                && (returnsVoid() || isIntegerLike(returnType) || isArrayType(returnType));
    }

    public static boolean isIntegerLike(String type) {
        return type.length() == 1 && "ZBCSI".contains(type);
    }

    /**
     * Arrays are pointer-passed; there is no heap, so an array parameter/return is only ever a handle to
     * storage that outlives the call — see {@link io.github.jabrena.juno.lowering.BytecodeToIr} for the
     * (narrow, always-sound) rules on when returning one is actually accepted.
     */
    public static boolean isArrayType(String type) {
        return type.length() == 2 && type.charAt(0) == '[' && "ZBCSI".indexOf(type.charAt(1)) >= 0;
    }

    /** The element type character of an array descriptor, e.g. {@code B} for {@code [B}. */
    public static char arrayElementDescriptor(String arrayType) {
        return arrayType.charAt(1);
    }

    private static boolean isSupportedParameterType(String type) {
        return isIntegerLike(type) || isArrayType(type);
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
