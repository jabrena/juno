package io.github.jabrena.juno.classfile;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The board aliases expose only public methods whose names and JVM signatures exist in the JDK server API. */
class JdkHttpServerAliasCompatibilityTest {
    private final JdkClassAliases aliases = new JdkClassAliases();

    @Test
    void httpServerPublicMethodsMatchTheJdk() {
        assertMethodsMatchJdk(
                io.github.jabrena.juno.jdk.net.httpserver.HttpServer.class,
                HttpServer.class);
    }

    @Test
    void httpsServerPublicMethodsMatchTheJdk() {
        assertMethodsMatchJdk(
                io.github.jabrena.juno.jdk.net.httpserver.HttpsServer.class,
                HttpsServer.class);
        assertMethodsMatchJdk(io.github.jabrena.juno.jdk.net.httpserver.HttpsConfigurator.class,
                HttpsConfigurator.class);
        assertConstructorsMatchJdk(io.github.jabrena.juno.jdk.net.httpserver.HttpsConfigurator.class,
                HttpsConfigurator.class);
    }

    @Test
    void exchangeHandlerAndHeadersPublicMethodsMatchTheJdk() {
        assertMethodsMatchJdk(
                io.github.jabrena.juno.jdk.net.httpserver.HttpExchange.class,
                HttpExchange.class);
        assertMethodsMatchJdk(
                io.github.jabrena.juno.jdk.net.httpserver.HttpHandler.class,
                HttpHandler.class);
        assertMethodsMatchJdk(
                io.github.jabrena.juno.jdk.net.httpserver.Headers.class,
                Headers.class);
    }

    @Test
    void listenerAddressTypeMatchesJdkNameAndPortAccessor() {
        assertThat(aliases.apply("io/github/jabrena/juno/jdk/net/InetSocketAddress"))
                .isEqualTo("java/net/InetSocketAddress");
        assertMethodsMatchJdk(io.github.jabrena.juno.jdk.net.InetSocketAddress.class,
                java.net.InetSocketAddress.class);
        assertConstructorsMatchJdk(io.github.jabrena.juno.jdk.net.InetSocketAddress.class,
                java.net.InetSocketAddress.class);
    }

    private void assertMethodsMatchJdk(Class<?> boardClass, Class<?> jdkClass) {
        assertThat(aliases.apply(boardClass.getName().replace('.', '/')))
                .isEqualTo(jdkClass.getName().replace('.', '/'));

        Set<String> jdkSignatures = Arrays.stream(jdkClass.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(this::signature)
                .collect(Collectors.toSet());
        Set<String> boardSignatures = Arrays.stream(boardClass.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(method -> aliases.apply(signature(method)))
                .collect(Collectors.toSet());

        assertThat(boardSignatures)
                .as("public methods on %s must be JDK methods on %s", boardClass.getName(), jdkClass.getName())
                .isNotEmpty()
                .isSubsetOf(jdkSignatures);
    }

    private String signature(Method method) {
        return method.getName() + methodDescriptor(method.getParameterTypes(), method.getReturnType());
    }

    private void assertConstructorsMatchJdk(Class<?> boardClass, Class<?> jdkClass) {
        Set<String> jdkSignatures = Arrays.stream(jdkClass.getConstructors())
                .map(this::constructorSignature)
                .collect(Collectors.toSet());
        Set<String> boardSignatures = Arrays.stream(boardClass.getConstructors())
                .map(constructor -> aliases.apply(constructorSignature(constructor)))
                .collect(Collectors.toSet());
        assertThat(boardSignatures).isNotEmpty().isSubsetOf(jdkSignatures);
    }

    private String constructorSignature(Constructor<?> constructor) {
        return "<init>" + methodDescriptor(constructor.getParameterTypes(), void.class);
    }

    private String methodDescriptor(Class<?>[] parameters, Class<?> result) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameter : parameters) {
            descriptor.append(typeDescriptor(parameter));
        }
        return descriptor.append(')').append(typeDescriptor(result)).toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        return "D";
    }
}
