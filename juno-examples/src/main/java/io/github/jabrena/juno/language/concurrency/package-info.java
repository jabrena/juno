/**
 * Concurrency examples for the UNO R4 WiFi and UNO Q, limited to the concurrency APIs Juno lowers: volatile fields,
 * {@code synchronized}, {@link java.util.concurrent.locks.ReentrantLock}, the atomic {@code Integer}, {@code Boolean}
 * and {@code Long} cells, virtual threads, and {@link java.util.concurrent.StructuredTaskScope}.
 *
 * <ul>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.LostUpdates} — plain, volatile, monitor, lock and atomic
 *       increments.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.Mutexes} — Peterson's algorithm and a compare-and-set spin
 *       lock.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.DiningPhilosophers} — deadlock avoidance by lock ordering.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.BoundedBuffer} — a producer and a consumer over a bounded
 *       buffer.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.StructuredRace} — first-success and all-or-nothing joiners
 *       with cancellation.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.LazyInit} — double-checked locking with a volatile field.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.MonitorHappensBefore} — reentrant monitor ordering.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.VolatileHappensBefore} — volatile publication.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.ThreadStartAndJoin} — thread start and join ordering.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.SleepAndYield} — sleep and yield retain monitors.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.FinalFieldPublication} — final-field freeze semantics.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.Causality} — no out-of-thin-air values.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.WordTearing} — independent adjacent array elements.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.ReferenceAtomicity} — atomic reference access.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.VolatileLongAtomicity} — atomic volatile 64-bit access.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.Locks} — concrete reentrant locking, {@code tryLock} and
 *       disciplined {@code finally}/{@code unlock} cleanup.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.Atomics} — lock-free {@code AtomicInteger},
 *       {@code AtomicLong} and {@code AtomicBoolean} operations.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.WaitNotification} and
 *       {@link io.github.jabrena.juno.language.concurrency.Interruption} — JVM-only source examples for JLS 17.2.</li>
 *   <li>{@link io.github.jabrena.juno.language.concurrency.Semaphores},
 *       {@link io.github.jabrena.juno.language.concurrency.CountDownLatchExample},
 *       {@link io.github.jabrena.juno.language.concurrency.PhaserExample},
 *       {@link io.github.jabrena.juno.language.concurrency.CyclicBarrierExample},
 *       {@link io.github.jabrena.juno.language.concurrency.ExchangerExample},
 *       {@link io.github.jabrena.juno.language.concurrency.ReadWriteLockExample} and
 *       {@link io.github.jabrena.juno.language.concurrency.BlockingQueueExample} — JVM-only examples of higher-level
 *       {@code java.util.concurrent} coordination objects that Juno intentionally does not lower.</li>
 * </ul>
 *
 * <p>Each board entry point writes its results to USB serial at 115200 baud, and each total can be checked against the
 * value printed beside it. The board entry points use no {@code wait}/{@code notify}, interruption, semaphores or
 * latches, since Juno does not lower them; the JVM-only classes above make those APIs explicit and testable without
 * pretending they are available on a board.
 */
package io.github.jabrena.juno.language.concurrency;
