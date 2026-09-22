package io.github.jabrena.juno.api.io.net.http;

/**
 * Named HTTP status codes for {@link HttpServer#respond}'s {@code status} parameter, which stays
 * a plain {@code int} — these are ordinary compile-time-constant {@code int} fields, not a new
 * intrinsic or type, exactly like {@link io.github.jabrena.juno.api.io.Gpio#OUTPUT}.
 */
public final class HttpStatus {
    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int NO_CONTENT = 204;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int CONFLICT = 409;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int SERVICE_UNAVAILABLE = 503;

    private HttpStatus() {
    }
}
