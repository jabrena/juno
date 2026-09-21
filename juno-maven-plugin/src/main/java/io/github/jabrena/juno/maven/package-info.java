/**
 * The Maven integration for Juno: goals that generate a sketch, verify and flash it with Arduino
 * CLI, and open its serial monitor.
 *
 * <ul>
 *   <li>{@link io.github.jabrena.juno.maven.CompileMojo} ({@code juno:compile}) — generates the
 *       {@code .S} assembly, C++ runtime shim, and {@code .ino} wrapper for a configured entry
 *       point.</li>
 *   <li>{@link io.github.jabrena.juno.maven.VerifyMojo} ({@code juno:verify}) — compiles, then
 *       builds the sketch with {@code arduino-cli compile}, no board required.</li>
 *   <li>{@link io.github.jabrena.juno.maven.UploadMojo} ({@code juno:upload}) — compiles, verifies,
 *       resolves the board's serial port, and flashes it.</li>
 *   <li>{@link io.github.jabrena.juno.maven.MonitorMojo} ({@code juno:monitor}) — resolves the
 *       board's serial port and opens an interactive {@code arduino-cli monitor} session.</li>
 *   <li>{@link io.github.jabrena.juno.maven.EnvMojo} ({@code juno:env}) — exports a git-ignored
 *       {@code .env} file into the Maven JVM's real process environment before {@code compile}
 *       runs, so a program's {@code System.getenv(...)} reads (Wi-Fi credentials, hosts, paths)
 *       resolve without exporting shell variables by hand.</li>
 * </ul>
 *
 * <p>{@link io.github.jabrena.juno.maven.AbstractJunoMojo} and
 * {@link io.github.jabrena.juno.maven.AbstractArduinoMojo} hold the shared configuration
 * (entry point, classpath, output directory, FQBN/port overrides) and behavior (invoking
 * {@link io.github.jabrena.juno.JunoCompiler}, resolving a board's port) the compile/verify/upload
 * goals build on. The package-private {@code ArduinoCli}, {@code CommandExecutor}, and
 * {@code SystemCommandExecutor} types wrap {@code arduino-cli} invocations behind a small,
 * testable seam, with {@code ArduinoCliException} as their failure type.
 */
package io.github.jabrena.juno.maven;
