package io.github.jabrena.juno;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import io.github.jabrena.juno.annotations.Watchdog;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Examples are user programs: they may use only juno-api and the JDK, never the compiler.
 *
 * <p>Examples share package names with juno-api and juno-compiler (e.g. {@code io.github.jabrena.juno.api.io}), so
 * the rule tells them apart by where each class comes from rather than by package. API classes are taken from
 * juno-api's own output, not resolved through the test classpath, where this module's test fakes shadow some of them.
 */
class ExamplesArchitectureTest {

    @Test
    void examplesDependOnlyOnTheApiAndTheJdk() throws URISyntaxException {
        JavaClasses examples = new ClassFileImporter().importPath(Path.of("target/classes"));
        Set<String> examplesNames = names(examples);
        Set<String> apiNames = names(new ClassFileImporter().importLocations(Set.of(apiLocation())));

        DescribedPredicate<JavaClass> apiExamplesOrJdk = DescribedPredicate.describe("juno-api, the examples or the JDK",
                type -> isJdkOrIn(type.getBaseComponentType(), apiNames, examplesNames));

        classes().should().onlyDependOnClassesThat(apiExamplesOrJdk)
                .because("examples are programs compiled against juno-api; the compiler is a test-only dependency here")
                .check(examples);
    }

    private static boolean isJdkOrIn(JavaClass type, Set<String> apiNames, Set<String> examplesNames) {
        return type.isPrimitive() || type.getPackageName().startsWith("java.")
                || apiNames.contains(type.getName()) || examplesNames.contains(type.getName());
    }

    private static Set<String> names(JavaClasses classes) {
        return classes.stream().map(JavaClass::getName).collect(Collectors.toSet());
    }

    /** juno-api's classes directory in a reactor build, its jar otherwise; anchored on an annotation nothing shadows. */
    private static Location apiLocation() throws URISyntaxException {
        return Location.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
