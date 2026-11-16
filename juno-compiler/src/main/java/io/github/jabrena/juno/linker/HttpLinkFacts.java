package io.github.jabrena.juno.linker;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a program does with {@code java.net.http}, as the linker decided it from the program's reachable code: the
 * answers to the {@code NativeHttpSchemes} and {@code NativeHttpFeatures} queries for that program (see
 * {@link HttpQueryFolding}).
 *
 * @param facts what the program does
 */
record HttpLinkFacts(Set<Fact> facts) {
    /** Nothing: what the first linking pass assumes, so that no optional request code is linked. */
    static final HttpLinkFacts NONE = of();

    /** One thing a program may do with {@code java.net.http}, which code is linked only for. */
    enum Fact {
        /** A request may go to an {@code http} URI. */
        HTTP,
        /** A request may go to an {@code https} URI. */
        HTTPS,
        /** The program reads a response's headers, or follows redirects, which read {@code Location}. */
        RESPONSE_HEADERS,
        /** The program asks a client to follow redirects. */
        REDIRECTS,
        /** The program sets a method of its own with {@code HttpRequest.Builder.method}, the one way to CONNECT. */
        CUSTOM_METHODS,
        /** The program sets a request or connect timeout. */
        TIMEOUTS
    }

    HttpLinkFacts {
        facts = facts.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(facts));
    }

    static HttpLinkFacts of(Fact... facts) {
        return new HttpLinkFacts(Set.of(facts));
    }

    /** Whether the program does {@code fact}. */
    boolean has(Fact fact) {
        return facts.contains(fact);
    }

    /** These facts with {@code other}'s added. */
    HttpLinkFacts with(HttpLinkFacts other) {
        if (other.facts.isEmpty()) {
            return this;
        }
        EnumSet<Fact> union = EnumSet.copyOf(other.facts);
        union.addAll(facts);
        return new HttpLinkFacts(union);
    }
}
