package io.github.jabrena.juno.classfile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdkClassAliasesTest {
    private final JdkClassAliases aliases = new JdkClassAliases();

    @Test
    @DisplayName("a board-side JDK class name is read as the JDK class it stands for")
    void readsTheClassNameAsTheJdkOne() {
        assertThat(aliases.apply("io/github/jabrena/juno/jdk/net/http/HttpClient")).isEqualTo("java/net/http/HttpClient");
        assertThat(aliases.apply("io/github/jabrena/juno/jdk/net/http/HttpClient$Builder"))
                .isEqualTo("java/net/http/HttpClient$Builder");
    }

    @Test
    @DisplayName("every board-side name inside a descriptor is read as the JDK one")
    void rewritesDescriptors() {
        assertThat(aliases.apply("(Lio/github/jabrena/juno/jdk/net/http/HttpRequest;"
                + "Lio/github/jabrena/juno/jdk/net/http/HttpResponse$BodyHandler;)"
                + "Lio/github/jabrena/juno/jdk/net/http/HttpResponse;"))
                .isEqualTo("(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)"
                        + "Ljava/net/http/HttpResponse;");
    }

    @Test
    @DisplayName("any other string is returned as it was read")
    void leavesOtherStringsAlone() {
        String hardware = "io/github/jabrena/juno/hardware/net/http/Json";

        assertThat(aliases.apply(hardware)).isSameAs(hardware);
        assertThat(aliases.apply("GET")).isEqualTo("GET");
    }

    @Test
    @DisplayName("a board-side javax class is read as the javax one, alone or next to a java one")
    void readsJdkxAsJavax() {
        assertThat(aliases.apply("io/github/jabrena/juno/javax/net/ssl/SSLContext")).isEqualTo("javax/net/ssl/SSLContext");
        assertThat(aliases.apply("(Lio/github/jabrena/juno/javax/net/ssl/SSLContext;)"
                + "Lio/github/jabrena/juno/jdk/net/http/HttpClient$Builder;"))
                .isEqualTo("(Ljavax/net/ssl/SSLContext;)Ljava/net/http/HttpClient$Builder;");
    }
}
