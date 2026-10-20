package io.github.jabrena.juno;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.api.thirdparty.lego.rcxfirmware.RcxFirmwareImage;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Examples are user programs: they may use only juno-api and the JDK, never the compiler.
 *
 * <p>Examples share package names with juno-api and juno-compiler (e.g. {@code io.github.jabrena.juno.api.hardware.gpio}), so
 * the rule tells them apart by where each class comes from rather than by package. API classes are taken from
 * juno-api's own output, not resolved through the test classpath, where this module's test fakes shadow some of them.
 */
class ExamplesArchitectureTest {

    @Test
    void examplesDependOnlyOnTheApiAndTheJdk() throws URISyntaxException {
        JavaClasses examples = new ClassFileImporter().importPath(Path.of("target/classes"));
        Set<String> examplesNames = names(examples);
        Set<String> apiNames = names(new ClassFileImporter().importLocations(Set.of(apiLocation())));
        Set<String> rcxFirmwareNames = names(new ClassFileImporter().importLocations(Set.of(rcxFirmwareLocation())));

        DescribedPredicate<JavaClass> allowedClasses = DescribedPredicate.describe(
                "juno-api, the RCX firmware image, the examples or the JDK",
                type -> isJdkOrIn(type.getBaseComponentType(), apiNames, rcxFirmwareNames, examplesNames));

        classes().should().onlyDependOnClassesThat(allowedClasses)
                .because("examples are programs compiled against board-side artifacts; the compiler is test-only")
                .check(examples);
    }

    private static boolean isJdkOrIn(
            JavaClass type, Set<String> apiNames, Set<String> rcxFirmwareNames, Set<String> examplesNames) {
        return type.isPrimitive() || type.getPackageName().startsWith("java.")
                || isKotlinAnnotation(type) || isScalaSerializationProxy(type) || isClojureRuntime(type)
                || apiNames.contains(type.getName()) || rcxFirmwareNames.contains(type.getName())
                || examplesNames.contains(type.getName());
    }

    /**
     * Kotlin compiles each example against annotations that add no code the board could run ({@code @Metadata},
     * {@code @JvmStatic}, and the JetBrains nullability markers such as {@code @NotNull}). Any other kotlin-stdlib
     * reference, such as a helper call, still fails the rule.
     */
    private static boolean isKotlinAnnotation(JavaClass type) {
        String name = type.getName();
        return name.startsWith("org.jetbrains.annotations.")
                || (name.startsWith("kotlin.") && type.isAnnotation());
    }

    /**
     * Scala gives every top-level object a {@code writeReplace} that hands Java serialization a
     * {@code ModuleSerializationProxy}. Only serialization calls it, and Juno never lowers it because {@code main} does
     * not reach it, so the proxy adds no code the board could run.
     */
    private static boolean isScalaSerializationProxy(JavaClass type) {
        return type.getName().equals("scala.runtime.ModuleSerializationProxy");
    }

    /**
     * Clojure's ahead-of-time output calls the {@code clojure.lang} runtime and {@code clojure.core} functions, which
     * the board does not have. The compiler lowers the calls it supports ({@code ClojureSupport}) and rejects every
     * other one at link time, which {@code LanguageExamplesCompilationTest} checks for each example.
     */
    private static boolean isClojureRuntime(JavaClass type) {
        String name = type.getName();
        return name.startsWith("clojure.lang.") || name.startsWith("clojure.core");
    }

    private static Set<String> names(JavaClasses classes) {
        return classes.stream().map(JavaClass::getName).collect(Collectors.toSet());
    }

    /** juno-api's classes directory in a reactor build, its jar otherwise; anchored on an annotation nothing shadows. */
    private static Location apiLocation() throws URISyntaxException {
        return Location.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static Location rcxFirmwareLocation() throws URISyntaxException {
        return Location.of(RcxFirmwareImage.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
