package io.github.jabrena.juno;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Keeps juno-api a self-contained programming model: programs compile against it alone, so it must not reach into the
 * compiler (or anything else) and the compiler's packages must not move into it.
 */
@AnalyzeClasses(packages = "io.github.jabrena.juno", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiArchitectureTest {

    private static final String[] API_PACKAGES = {"io.github.jabrena.juno.api..", "io.github.jabrena.juno.annotations.."};

    @ArchTest
    static final ArchRule apiLivesOnlyInApiPackages = classes()
            .should().resideInAnyPackage(API_PACKAGES)
            .because("juno-api holds only the api and annotations packages; compiler code belongs in juno-compiler");

    @ArchTest
    static final ArchRule apiDependsOnlyOnItselfAndTheJdk = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "io.github.jabrena.juno.api..", "io.github.jabrena.juno.annotations..", "java..",
                    "org.jspecify.annotations..")
            .because("programs compile against juno-api alone, so it must not use the compiler or third-party code");
}
