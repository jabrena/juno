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
 * </ul>
 *
 * <p>Each example writes its results to USB serial at 115200 baud, and each total can be checked against the value
 * printed beside it. The examples use no {@code wait}/{@code notify}, semaphores or latches, since Juno does not lower
 * them.
 */
package io.github.jabrena.juno.language.concurrency;
