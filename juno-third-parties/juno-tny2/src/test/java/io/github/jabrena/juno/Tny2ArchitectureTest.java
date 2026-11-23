package io.github.jabrena.juno;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/** The TNY2 model builder is independent of the Java-to-Arduino compiler and API. */
@DisplayName("TNY2 model builder architecture rules")
class Tny2ArchitectureTest {

    @Test
    @DisplayName("The model builder depends only on itself, the JDK and JSpecify")
    void shouldDependOnlyOnItselfTheJdkAndJSpecify() {
        // Given
        JavaClasses builderClasses = new ClassFileImporter().importPath(Path.of("target/classes"));
        Set<String> builderNames = names(builderClasses);

        DescribedPredicate<JavaClass> builderJdkOrJSpecify = DescribedPredicate.describe(
                "the TNY2 module, the JDK or JSpecify",
                type -> isAllowed(type.getBaseComponentType(), builderNames));

        // When & Then
        classes().should().onlyDependOnClassesThat(builderJdkOrJSpecify)
                .because("TNY2 model building is a separate toolchain, not part of juno-compiler or juno-api")
                .check(builderClasses);
    }

    private static boolean isAllowed(JavaClass type, Set<String> builderNames) {
        return type.isPrimitive() || type.getPackageName().startsWith("java.")
                || type.getPackageName().startsWith("org.jspecify.annotations")
                || builderNames.contains(type.getName());
    }

    private static Set<String> names(JavaClasses classes) {
        return classes.stream().map(JavaClass::getName).collect(Collectors.toSet());
    }
}
