package io.github.jabrena.juno.classfile;

/**
 * Lets {@code juno-api} supply board-side implementations of JDK classes that javac must still resolve to the real
 * JDK. A class written in {@code io.github.jabrena.juno.jdk.net.http} is read as {@code java.net.http}: every
 * constant-pool string that names it — its own name, the class references, the field and method descriptors — has the
 * prefix {@value #BOARD_PREFIX} replaced by {@value #JDK_PREFIX} as the class file is read. A program compiled against
 * {@code java.net.http.HttpClient} then links to the board-side class without either side knowing about the other, and
 * the board-side class stays an ordinary class that the JVM can load and unit-test.
 *
 * <p>Classes under {@value #BOARD_X_PREFIX} stand for {@code javax} ones in the same way, such as the board-side
 * {@code javax.net.ssl.SSLContext}. Only names under the board prefixes change; every other string is returned as it
 * was read.
 */
final class JdkClassAliases {
    /** The package prefix, in internal form, of the board-side JDK classes. */
    static final String BOARD_PREFIX = "io/github/jabrena/juno/jdk/";
    /** What {@link #BOARD_PREFIX} stands for. */
    static final String JDK_PREFIX = "java/";
    /** The package prefix, in internal form, of the board-side {@code javax} classes. */
    static final String BOARD_X_PREFIX = "io/github/jabrena/juno/jdkx/";
    /** What {@link #BOARD_X_PREFIX} stands for. */
    static final String JDK_X_PREFIX = "javax/";

    /** Returns {@code utf8} with every occurrence of a board prefix read as the JDK prefix it stands for. */
    String apply(String utf8) {
        String read = utf8.contains(BOARD_PREFIX) ? utf8.replace(BOARD_PREFIX, JDK_PREFIX) : utf8;
        return read.contains(BOARD_X_PREFIX) ? read.replace(BOARD_X_PREFIX, JDK_X_PREFIX) : read;
    }
}
