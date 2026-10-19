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

/** The RCX firmware toolchain is independent of the Java-to-Arduino compiler and API. */
@DisplayName("RCX firmware architecture rules")
class RcxFirmwareArchitectureTest {

    @Test
    @DisplayName("The firmware generator depends only on itself, the JDK and JSpecify")
    void shouldDependOnlyOnItselfTheJdkAndJSpecify() {
        // Given
        JavaClasses firmwareClasses = new ClassFileImporter().importPath(Path.of("target/classes"));
        Set<String> firmwareNames = names(firmwareClasses);

        DescribedPredicate<JavaClass> firmwareJdkOrJSpecify = DescribedPredicate.describe(
                "the RCX firmware module, the JDK or JSpecify",
                type -> isAllowed(type.getBaseComponentType(), firmwareNames));

        // When & Then
        classes().should().onlyDependOnClassesThat(firmwareJdkOrJSpecify)
                .because("RCX firmware generation is a separate toolchain, not part of juno-compiler or juno-api")
                .check(firmwareClasses);
    }

    private static boolean isAllowed(JavaClass type, Set<String> firmwareNames) {
        return type.isPrimitive() || type.getPackageName().startsWith("java.")
                || type.getPackageName().startsWith("org.jspecify.annotations")
                || firmwareNames.contains(type.getName());
    }

    private static Set<String> names(JavaClasses classes) {
        return classes.stream().map(JavaClass::getName).collect(Collectors.toSet());
    }
}
