package io.github.jabrena.juno.backend;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the board-specific runtime behind one seam: code generation reaches a board's Arduino core only through
 * {@code CoreRuntime}, so adding a board is adding one core implementation, never a branch in the code generator.
 */
@AnalyzeClasses(packages = "io.github.jabrena.juno", importOptions = ImportOption.DoNotIncludeTests.class)
class BackendArchitectureTest {

    private static final String CORE_PACKAGE = "io.github.jabrena.juno.backend.runtime.core..";

    /** The per-core implementations; only {@code CoreRuntime.of} may name them. */
    private static final String[] CORE_IMPLEMENTATIONS = {
            "io.github.jabrena.juno.backend.runtime.core.RenesasCoreRuntime",
            "io.github.jabrena.juno.backend.runtime.core.ZephyrCoreRuntime",
            "io.github.jabrena.juno.backend.runtime.core.MegaAvrCoreRuntime"};

    @ArchTest
    static final ArchRule codeGenerationNeverNamesACoreImplementation = noClasses()
            .that().resideOutsideOfPackage(CORE_PACKAGE)
            .should().dependOnClassesThat(describe("are a per-core runtime implementation",
                    (JavaClass javaClass) -> javaClassIsCoreImplementation(javaClass)))
            .because("the code generator reaches the board's core through CoreRuntime alone; a branch on a specific core "
                    + "belongs in that core's implementation");

    @ArchTest
    static final ArchRule coreImplementationsArePackagePrivate = noClasses()
            .that().resideInAPackage(CORE_PACKAGE)
            .and().doNotHaveSimpleName("CoreRuntime")
            .should().bePublic()
            .because("the per-core implementations are package-private; CoreRuntime is their only public face");

    private static boolean javaClassIsCoreImplementation(JavaClass javaClass) {
        for (String name : CORE_IMPLEMENTATIONS) {
            if (javaClass.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
