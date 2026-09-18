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
                && (returnsVoid() || isIntegerLike(returnType));
    }

    public static boolean isIntegerLike(String type) {
        return type.length() == 1 && "ZBCSI".contains(type);
    }

    /** {@code int[]} parameters are pointer-passed; arrays as return types are not yet supported. */
    public static boolean isIntArray(String type) {
        return type.equals("[I");
    }

    private static boolean isSupportedParameterType(String type) {
        return isIntegerLike(type) || isIntArray(type);
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
