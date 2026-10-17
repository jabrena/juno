package io.github.jabrena.juno.usecases.supabase.commons;

/** ASCII and PostgREST syntax recognized by the bounded Supabase example client. */
final class SupabaseSyntax {
    private SupabaseSyntax() {
    }

    static boolean supportsOperator(String operator) {
        return operator.equals("eq")
                || operator.equals("neq")
                || operator.equals("gt")
                || operator.equals("gte")
                || operator.equals("lt")
                || operator.equals("lte")
                || operator.equals("like")
                || operator.equals("ilike")
                || operator.equals("is");
    }

    static boolean identifierCharacter(int character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    static boolean unreserved(int character) {
        return identifierCharacter(character) || character == '-' || character == '.' || character == '~';
    }

    static int hex(int value) {
        return value < 10 ? '0' + value : 'A' + value - 10;
    }
}
