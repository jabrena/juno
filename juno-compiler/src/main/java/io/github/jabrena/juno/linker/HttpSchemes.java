package io.github.jabrena.juno.linker;

/**
 * Which URI schemes a program sends HTTP requests to, as the linker decided it from the program's
 * {@code URI.create(...)} calls: what {@code NativeHttpSchemes.http()} and {@code https()} answer for that program.
 *
 * @param http  whether a request may go to an {@code http} URI
 * @param https whether a request may go to an {@code https} URI
 */
record HttpSchemes(boolean http, boolean https) {
    /** No scheme: what the first linking pass assumes, so that neither request is linked while the URIs are found. */
    static final HttpSchemes NONE = new HttpSchemes(false, false);
    /** Both schemes: the answer when a URI is built from runtime data, which could hold either. */
    static final HttpSchemes BOTH = new HttpSchemes(true, true);

    /** This set with {@code other}'s schemes added. */
    HttpSchemes with(HttpSchemes other) {
        return new HttpSchemes(http || other.http, https || other.https);
    }
}
