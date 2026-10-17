package io.github.jabrena.juno.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExampleLanguageTest {

    @ParameterizedTest
    @CsvSource({
        "all, ALL",
        "java, JAVA",
        "KOTLIN, KOTLIN",
        "' scala ', SCALA",
        "clojure, CLOJURE"
    })
    void parsesSupportedValues(String configured, ExampleLanguage expected) {
        assertThat(ExampleLanguage.parse(configured)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "io.github.jabrena.juno.language.HelloWorld, JAVA",
        "io.github.jabrena.juno.language.concurrency.Mutexes, JAVA",
        "io.github.jabrena.juno.language.kotlin.HelloWorld, KOTLIN",
        "io.github.jabrena.juno.language.scala.HelloWorld, SCALA",
        "io.github.jabrena.juno.language.clojure.HelloWorld, CLOJURE"
    })
    void identifiesTheSourceLanguage(String mainClass, ExampleLanguage expected) {
        assertThat(ExampleLanguage.of(mainClass)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "io.github.jabrena.juno.language.HelloWorld",
        "io.github.jabrena.juno.language.kotlin.HelloWorld",
        "io.github.jabrena.juno.language.scala.HelloWorld",
        "io.github.jabrena.juno.language.clojure.HelloWorld"
    })
    void allIncludesEveryLanguage(String mainClass) {
        assertThat(ExampleLanguage.ALL.includes(mainClass)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "JAVA, io.github.jabrena.juno.language.HelloWorld, true",
        "JAVA, io.github.jabrena.juno.language.kotlin.HelloWorld, false",
        "KOTLIN, io.github.jabrena.juno.language.kotlin.HelloWorld, true",
        "SCALA, io.github.jabrena.juno.language.scala.HelloWorld, true",
        "CLOJURE, io.github.jabrena.juno.language.clojure.HelloWorld, true"
    })
    void includesOnlyTheSelectedLanguage(ExampleLanguage language, String mainClass, boolean expected) {
        assertThat(language.includes(mainClass)).isEqualTo(expected);
    }

    @Test
    void rejectsAnUnsupportedValue() {
        assertThatThrownBy(() -> ExampleLanguage.parse("groovy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported juno.language 'groovy'; expected one of: all, java, kotlin, scala, clojure");
    }

    @Test
    void rejectsAClassOutsideTheLanguageExamples() {
        assertThatThrownBy(() -> ExampleLanguage.of("io.github.jabrena.juno.api.Gpio"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not a language example: io.github.jabrena.juno.api.Gpio");
    }
}
