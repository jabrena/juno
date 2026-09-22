package io.github.jabrena.juno.api.io.net.http;

/**
 * Named HTTP methods for matching against {@link HttpServer#method()}. Like {@link HttpStatus},
 * these are ordinary compile-time-constant fields, not a new intrinsic or type: a {@code static
 * final String} initialized from a literal is itself a Java compile-time constant, so {@code
 * javac} inlines it at every use site — {@code method.equals(HttpMethod.GET)} compiles to exactly
 * the same bytecode as {@code method.equals("GET")}.
 */
public final class HttpMethod {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String PATCH = "PATCH";
    public static final String HEAD = "HEAD";
    public static final String OPTIONS = "OPTIONS";
    /** Safe, idempotent request with a body, as already supported by {@link HttpClient#query}. */
    public static final String QUERY = "QUERY";

    private HttpMethod() {
    }
}
