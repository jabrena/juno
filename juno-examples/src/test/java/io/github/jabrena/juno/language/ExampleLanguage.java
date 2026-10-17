package io.github.jabrena.juno.language;

import java.util.Locale;

/** Selects which source language contributes examples to the language compilation tests. */
enum ExampleLanguage {
    ALL("", ""),
    JAVA("io.github.jabrena.juno.language.", ""),
    KOTLIN("io.github.jabrena.juno.language.kotlin.", "Kotlin"),
    SCALA("io.github.jabrena.juno.language.scala.", "Scala"),
    CLOJURE("io.github.jabrena.juno.language.clojure.", "Clojure");

    static final String PROPERTY = "juno.language";
    private static final String VALUES = "all, java, kotlin, scala, clojure";

    private final String packagePrefix;
    private final String sketchPrefix;

    ExampleLanguage(String packagePrefix, String sketchPrefix) {
        this.packagePrefix = packagePrefix;
        this.sketchPrefix = sketchPrefix;
    }

    /** The configured language, or {@link #ALL} when {@value #PROPERTY} is absent. */
    static ExampleLanguage configured() {
        return parse(System.getProperty(PROPERTY, "all"));
    }

    static ExampleLanguage parse(String configured) {
        try {
            return valueOf(configured.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported " + PROPERTY + " '" + configured + "'; expected one of: " + VALUES, exception);
        }
    }

    /** Whether this selection includes the example's class. */
    boolean includes(String mainClass) {
        return this == ALL || this == of(mainClass);
    }

    /** The source language that owns an example class. */
    static ExampleLanguage of(String mainClass) {
        if (mainClass.startsWith(KOTLIN.packagePrefix)) {
            return KOTLIN;
        }
        if (mainClass.startsWith(SCALA.packagePrefix)) {
            return SCALA;
        }
        if (mainClass.startsWith(CLOJURE.packagePrefix)) {
            return CLOJURE;
        }
        if (mainClass.startsWith(JAVA.packagePrefix)) {
            return JAVA;
        }
        throw new IllegalArgumentException("Not a language example: " + mainClass);
    }

    String sketchPrefix() {
        return sketchPrefix;
    }
}
