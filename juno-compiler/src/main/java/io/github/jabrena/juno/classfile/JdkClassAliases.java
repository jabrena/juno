package io.github.jabrena.juno.classfile;

/**
 * Lets {@code juno-api} supply board-side implementations of JDK classes that javac must still resolve to the real
 * JDK. A class written in {@code io.github.jabrena.juno.jdk.net.http} is read as {@code java.net.http}: every
 * constant-pool string that names it — its own name, the class references, the field and method descriptors — has the
 * prefix {@value #BOARD_PREFIX} replaced by {@value #JDK_PREFIX} as the class file is read. A program compiled against
 * {@code java.net.http.HttpClient} then links to the board-side class without either side knowing about the other, and
 * the board-side class stays an ordinary class that the JVM can load and unit-test.
 *
 * <p>Only names under the board prefix change; every other string is returned as it was read.
 */
final class JdkClassAliases {
    /** The package prefix, in internal form, of the board-side JDK classes. */
    static final String BOARD_PREFIX = "io/github/jabrena/juno/jdk/";
    /** What {@link #BOARD_PREFIX} stands for. */
    static final String JDK_PREFIX = "java/";

    /** Returns {@code utf8} with every occurrence of the board prefix read as the JDK prefix. */
    String apply(String utf8) {
        return utf8.contains(BOARD_PREFIX) ? utf8.replace(BOARD_PREFIX, JDK_PREFIX) : utf8;
    }
}
