## ADDED Requirements

### Requirement: One carrier per core
On the UNO R4 WiFi, UNO Q, Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W, the runtime SHALL run virtual threads on one carrier per core: one carrier on the UNO R4 WiFi and UNO Q, two carriers on the Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W. The `main` thread SHALL remain a platform thread with its own stack. Whether a core uses carriers SHALL be decided by its core runtime, never by a board-specific branch in the backend.

#### Scenario: Single-core board
- **WHEN** a program that starts virtual threads is compiled for the UNO R4 WiFi or UNO Q
- **THEN** the generated runtime SHALL contain exactly one carrier besides `main`

#### Scenario: Dual-core board runs in parallel
- **WHEN** a program starts two CPU-bound virtual threads on the Nano ESP32, M5Stack Cardputer, ESP32-CAM or Pico 2 W
- **THEN** the two virtual threads SHALL be able to execute Java code at the same time on different cores

### Requirement: Virtual threads are continuations
A virtual thread SHALL hold no reserved stack while it is not mounted. When it parks or is preempted, the runtime SHALL copy its live frames into an arena chunk owned by the virtual thread, and when it resumes the runtime SHALL copy them back to the same addresses on the carrier where it started. A virtual thread SHALL be placed on the least-loaded carrier when it starts and SHALL NOT migrate to another carrier. Generated code for Java methods SHALL be the same as without continuations.

#### Scenario: More virtual threads than carriers
- **WHEN** a program on the UNO R4 WiFi starts eight virtual threads that each sleep, print and finish
- **THEN** all eight SHALL complete and their output SHALL match an OpenJDK run of the same source

#### Scenario: Parked thread state survives
- **WHEN** a virtual thread holding local variables and references parks in `Thread.sleep` while other virtual threads run
- **THEN** after it resumes its locals and the objects they reference SHALL be unchanged and reachable

### Requirement: Arena-bounded thread count and carrier stack size
The `juno.maxThreads` build parameter SHALL be removed. The number of live virtual threads and forks SHALL be bounded only by arena space. The `juno.Xss` parameter SHALL set the carrier stack size. Starting a virtual thread or fork when the arena cannot hold it SHALL stop the program with the message `cannot start virtual thread: arena full`.

#### Scenario: maxThreads no longer accepted
- **WHEN** a build passes `-Djuno.maxThreads`
- **THEN** the plugin SHALL no longer recognize the parameter and the thread count SHALL not be configurable

#### Scenario: Arena exhausted
- **WHEN** a program keeps starting virtual threads that never finish until the arena is full
- **THEN** the board SHALL print `cannot start virtual thread: arena full` and panic

### Requirement: Preemption preserved behind a scheduling policy
The runtime SHALL keep the current preemption behaviour: a 1 ms tick on the Thumb-2 boards and safepoints on the ESP32 family. A virtual thread SHALL be unmountable at a preemption point as well as at a blocking point. On Thumb-2 the tick SHALL only request the switch and the frame copy SHALL run in thread mode. The selection of the next virtual thread SHALL go through a scheduling-policy abstraction so that the policy can change without redesigning continuations.

#### Scenario: CPU-bound threads share a carrier
- **WHEN** two virtual threads run busy loops without blocking on the UNO R4 WiFi
- **THEN** both SHALL make progress

### Requirement: Structured concurrency forks are continuations
`StructuredTaskScope.fork` SHALL start a virtual thread on the carriers, bounded by arena space rather than a slot count. `join` SHALL park the owner. Cancelling a parked subtask SHALL drop its continuation and chunk; a running subtask SHALL be cancelled at its next park or safepoint. Shutdown policies, deadlines, failure propagation and `ScopedValue` inheritance SHALL keep their current semantics.

#### Scenario: More forks than carriers
- **WHEN** a scope forks six subtasks on the UNO Q and joins
- **THEN** all six results SHALL be available and the output SHALL match OpenJDK

#### Scenario: Cancellation releases the continuation
- **WHEN** a scope with a shutdown-on-failure policy cancels its parked subtasks after one fails
- **THEN** the cancelled subtasks SHALL no longer run and their arena chunks SHALL become collectable

### Requirement: Monitors owned by virtual threads
Monitors SHALL record the owning virtual thread. A virtual thread SHALL be allowed to park while holding a `synchronized` lock, and `Object.wait`/`notify` SHALL park and wake virtual threads.

#### Scenario: Park inside synchronized
- **WHEN** a virtual thread calls `Thread.sleep` inside a `synchronized` block and another virtual thread tries to enter the same monitor
- **THEN** the second thread SHALL wait until the first leaves the block, and other virtual threads SHALL keep running meanwhile

### Requirement: Multi-core safe runtime
On the dual-core boards the arena allocator, collector, monitors, atomics, run queue and per-carrier runtime state SHALL be safe when both carriers run Java code. The collector SHALL stop the other carrier through a flag that carrier checks at its safepoints (on the Pico 2 W, at the point confirmed in design) and SHALL scan its stack after it acknowledges. `volatile` and `synchronized` SHALL emit the memory barriers of the target architecture.

#### Scenario: Collection with both carriers busy
- **WHEN** both carriers allocate objects in a loop until collections occur on a dual-core board
- **THEN** no reachable object SHALL be freed and the program SHALL keep running

#### Scenario: Atomic counter across cores
- **WHEN** two virtual threads on different carriers each increment an `AtomicInteger` 10,000 times
- **THEN** the final value SHALL be 20,000

### Requirement: Offloaded I/O does not pin virtual threads
On the UNO Q, an offloaded I/O call SHALL keep the data the I/O worker uses in worker-owned storage, not on the calling virtual thread's stack, so that the calling virtual thread unmounts while it waits and other virtual threads keep running. No pointer held by an I/O worker or C++ library SHALL refer to a parked virtual thread's stack. On boards without I/O workers, blocking I/O SHALL run inline on its carrier as today.

#### Scenario: Other threads run during an HTTP request
- **WHEN** one virtual thread on the UNO Q performs an `HttpClient` request while another prints a counter every 100 ms
- **THEN** the counter SHALL keep printing during the request and the request SHALL return the same result as today

### Requirement: Diagnostics
The runtime risk report SHALL show the carrier stack size and estimated frozen-chunk sizes against arena headroom, and SHALL warn when a virtual thread may need more stack than its carrier provides.

#### Scenario: Deep virtual thread
- **WHEN** a virtual thread's estimated stack depth exceeds `juno.Xss`
- **THEN** the report SHALL emit a warning naming the thread's entry method

### Requirement: Out-of-scope boards and determinism
The UNO R3 and UNO WiFi Rev2 SHALL keep slot-based threads, and their generated output SHALL be byte-identical to before the change. Programs that use no threads SHALL produce byte-identical `.S` and `Shim.cpp` output on every board.

#### Scenario: AVR unchanged
- **WHEN** an existing threaded example is compiled for the UNO WiFi Rev2 before and after the change
- **THEN** the generated `.S` and `Shim.cpp` SHALL be identical

#### Scenario: No threads, no change
- **WHEN** Blink is compiled for any board before and after the change
- **THEN** the generated `.S` and `Shim.cpp` SHALL be identical

### Requirement: Validation on hardware
The change SHALL be validated by QEMU runs matching OpenJDK for virtual threads, scopes, monitors and `wait`/`notify`, and by one dedicated example (many virtual threads, parking, `StructuredTaskScope`, a `synchronized` section that parks, offloaded I/O on the UNO Q) uploaded with `juno:upload` and checked with `juno:monitor` on the UNO R4 WiFi, UNO Q, Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W before merge.

#### Scenario: Hardware confirmation
- **WHEN** the dedicated example runs on each of the six boards
- **THEN** its serial output observed through `juno:monitor` SHALL match the expected output
