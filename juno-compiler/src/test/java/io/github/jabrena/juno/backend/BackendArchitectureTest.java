package io.github.jabrena.juno.backend;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the backend's dependencies pointing one way: the code generators ({@code backend}, {@code thumb2},
 * {@code avr}) depend on the runtime, the per-core implementations ({@code runtime.core}) depend on the shared runtime
 * ({@code runtime}), and everything depends on the contracts in {@code runtime.spi}, which depend on nothing else in
 * the backend. Code generation reaches a board's Arduino core only through {@code CoreRuntime}, so adding a board is
 * adding one core implementation, never a branch in the code generator.
 */
@AnalyzeClasses(packages = "io.github.jabrena.juno", importOptions = ImportOption.DoNotIncludeTests.class)
class BackendArchitectureTest {

    private static final String CODEGEN = "io.github.jabrena.juno.backend";
    private static final String THUMB2 = "io.github.jabrena.juno.backend.thumb2..";
    private static final String AVR = "io.github.jabrena.juno.backend.avr..";
    private static final String RUNTIME = "io.github.jabrena.juno.backend.runtime";
    private static final String CORE = "io.github.jabrena.juno.backend.runtime.core..";
    private static final String SPI = "io.github.jabrena.juno.backend.runtime.spi..";

    /** The per-core implementations; only {@code CoreRuntimes.of} may name them. */
    private static final String[] CORE_IMPLEMENTATIONS = {
            "io.github.jabrena.juno.backend.runtime.core.RenesasCoreRuntime",
            "io.github.jabrena.juno.backend.runtime.core.ZephyrCoreRuntime",
            "io.github.jabrena.juno.backend.runtime.core.MegaAvrCoreRuntime"};

    @ArchTest
    static final ArchRule runtimeNeverDependsOnCodeGeneration = noClasses()
            .that().resideInAnyPackage(RUNTIME, CORE, SPI)
            .should().dependOnClassesThat().resideInAnyPackage(CODEGEN, THUMB2, AVR)
            .because("the runtime shim is what generated code links against; it must not know how that code is "
                    + "generated, so the code generators can depend on it and not the other way round");

    @ArchTest
    static final ArchRule sharedRuntimeNeverDependsOnACore = noClasses()
            .that().resideInAnyPackage(RUNTIME, SPI)
            .should().dependOnClassesThat().resideInAPackage(CORE)
            .because("each core implements CoreRuntime on top of the shared runtime, so the shared runtime may only "
                    + "see a core through the CoreRuntime contract");

    @ArchTest
    static final ArchRule contractsDependOnNothingElseInTheBackend = noClasses()
            .that().resideInAPackage(SPI)
            .should().dependOnClassesThat().resideInAnyPackage(CODEGEN, THUMB2, AVR, RUNTIME, CORE)
            .because("CoreRuntime and ShimFeature are the contracts every other backend package depends on");

    @ArchTest
    static final ArchRule coresLiveInTheCorePackage = classes()
            .that().implement(CoreRuntime.class)
            .should().resideInAPackage(CORE)
            .because("a new board's Arduino core is one CoreRuntime implementation in runtime.core, created by "
                    + "CoreRuntimes.of");

    @ArchTest
    static final ArchRule codeGenerationNeverNamesACoreImplementation = noClasses()
            .that().resideOutsideOfPackage(CORE)
            .should().dependOnClassesThat(describe("are a per-core runtime implementation",
                    (JavaClass javaClass) -> javaClassIsCoreImplementation(javaClass)))
            .because("the code generator reaches the board's core through CoreRuntime alone; a branch on a specific core "
                    + "belongs in that core's implementation");

    @ArchTest
    static final ArchRule coreImplementationsArePackagePrivate = noClasses()
            .that().resideInAPackage(CORE)
            .and().doNotHaveSimpleName("CoreRuntimes")
            .should().bePublic()
            .because("the per-core implementations are package-private; CoreRuntimes.of is their only public face");

    private static boolean javaClassIsCoreImplementation(JavaClass javaClass) {
        for (String name : CORE_IMPLEMENTATIONS) {
            if (javaClass.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
