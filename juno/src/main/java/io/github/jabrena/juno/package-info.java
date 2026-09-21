/**
 * Juno's top-level compiler entry points: the CLI, the embeddable compiler API, and the small
 * value types that connect them.
 *
 * <ul>
 *   <li>{@link io.github.jabrena.juno.Main} — the {@code compile}/{@code inspect} command-line
 *       tool ({@code java -jar juno-<version>.jar compile --main <class> --classpath <cp>}).</li>
 *   <li>{@link io.github.jabrena.juno.JunoCompiler} — the stable, embeddable API (used directly by
 *       {@code juno-maven-plugin}) wrapping the same pipeline behind a single {@code compile}
 *       call.</li>
 *   <li>{@link io.github.jabrena.juno.CompilationPipeline} — the compiler's stages, named and
 *       callable independently: classfiles -&gt; link -&gt; lower -&gt; optimize -&gt; generate.
 *       {@code JunoCompiler} runs them in sequence; this class exists so later tooling can hook
 *       into one stage's output without re-deriving it.</li>
 *   <li>{@link io.github.jabrena.juno.CompilationRequest} / {@link
 *       io.github.jabrena.juno.CompilationResult} / {@link io.github.jabrena.juno.CompilationReport}
 *       — the input (closed-world classpath, entry-point class, GC logging flag), output
 *       (generated assembly, C++ runtime shim, entry symbol), and diagnostic summary (reachable
 *       methods, IR block count, intrinsics used, runtime-risk findings) of one compilation.</li>
 *   <li>{@link io.github.jabrena.juno.CompileException} — the user-facing error type raised while
 *       linking or compiling, carrying a diagnostic message rather than an internal stack
 *       trace.</li>
 *   <li>{@link io.github.jabrena.juno.RuntimeLimits} — fixed constants shared between static
 *       analysis and the generated firmware (the 8&nbsp;KiB arena capacity, the runtime
 *       {@code String} slot size), kept in one place so both sides of that contract stay in
 *       sync.</li>
 * </ul>
 *
 * <p>The actual compiler stages live in sibling packages this package composes:
 * {@code classfile}/{@code bytecode} (parsing), {@code linker} (closed-world reachability),
 * {@code lowering}/{@code ir} (bytecode to Juno's own IR), {@code optimize} (IR-level passes),
 * {@code analysis} (runtime-risk estimation), and {@code backend} (Cortex-M4 assembly and C++
 * runtime shim generation). {@code api}, {@code annotations}, {@code board}, and {@code intrinsic}
 * define the Java-facing hardware API and the compiler's model of what it can target.
 */
package io.github.jabrena.juno;
