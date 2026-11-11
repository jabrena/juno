## ADDED Requirements

### Requirement: Two carriers on dual-core boards
On the Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W, the runtime SHALL run virtual threads on two carriers, one per core. The UNO R4 WiFi and UNO Q SHALL keep one carrier, and the AVR boards their slot-based threads. The number of carriers SHALL come from the core runtime, never from a board-specific branch in the backend.

#### Scenario: Parallel CPU-bound threads
- **WHEN** a program on a dual-core board starts two virtual threads that each run a long CPU-bound loop with no blocking call, and records which core each runs on
- **THEN** the two threads SHALL run on different cores and both SHALL make progress at the same time

#### Scenario: Single-core boards unchanged
- **WHEN** an existing threaded program is compiled for the UNO R4 WiFi, UNO Q or UNO WiFi Rev2 before and after the change
- **THEN** the generated `.S` and `Shim.cpp` SHALL be identical

#### Scenario: Pipeline without dual-core emulation
- **WHEN** a test pipeline compiles a dual-core board with `juno.carriers=1`
- **THEN** the compiler SHALL generate the single-carrier runtime and report one effective carrier
- **AND** omitting the property SHALL continue to generate the board's normal two-carrier runtime

### Requirement: Placement with carrier affinity
A new virtual thread or fork SHALL be placed on the carrier with the fewest live threads and SHALL stay on that carrier until it finishes. A thread whose entry can reach a device intrinsic that the board's core library allows only on one core SHALL be placed on that core's carrier, and the runtime risk report SHALL name it.

#### Scenario: Balanced placement
- **WHEN** a program starts four CPU-bound virtual threads on a dual-core board
- **THEN** two SHALL run on each carrier

#### Scenario: Device thread pinned
- **WHEN** a virtual thread on the Pico 2 W calls a Wi-Fi API and core-1 access is not confirmed safe for that API
- **THEN** the thread SHALL run on the core-0 carrier and the risk report SHALL say it is pinned

### Requirement: Serialized runtime
Runtime calls made from generated code SHALL execute under one recursive runtime lock, released while the calling thread parks or waits in a blocking device call, so that allocation, monitors, locks, latches, semaphores, atomics, runtime strings and device tables never run concurrently on two cores.

#### Scenario: Atomic counter across cores
- **WHEN** two virtual threads on different carriers each call `AtomicInteger.incrementAndGet` 10,000 times
- **THEN** the final value SHALL be 20,000

#### Scenario: Monitor across cores
- **WHEN** two virtual threads on different carriers each increment a shared field 10,000 times inside `synchronized` on the same object
- **THEN** the final value SHALL be 20,000

#### Scenario: Blocking call releases the runtime
- **WHEN** a virtual thread on one carrier waits in a Wi-Fi or HTTP call while a virtual thread on the other carrier allocates and prints
- **THEN** the second thread SHALL keep running during the call

### Requirement: Per-carrier runtime state
The current slot, pending exception, `ScopedValue` bindings and preemption state SHALL be kept per carrier, so that an exception, binding or preemption on one core never affects the thread running on the other.

#### Scenario: Exceptions on both cores
- **WHEN** a virtual thread on each carrier throws and catches its own exception repeatedly at the same time
- **THEN** each thread SHALL catch exactly its own exceptions

### Requirement: Stop-the-world collection across cores
A collection SHALL begin only once the other carrier is stopped: at a safepoint, waiting for the runtime lock, or inside a blocking device call that holds no arena pointer it may write. It SHALL scan that carrier's frames from its recorded stack pointer.

#### Scenario: Collection with both carriers allocating
- **WHEN** virtual threads on both carriers allocate short-lived objects in a loop while keeping references to some of them, until many collections have run
- **THEN** no reachable object SHALL be freed and both threads SHALL finish with the expected results

### Requirement: Memory model across cores
On dual-carrier boards, `volatile` accesses and monitor enter/exit SHALL emit the target's memory barrier (`dmb` on Cortex-M33, `memw` on Xtensa), and every `long`/`double` field access SHALL be atomic across cores. Programs without threads SHALL keep today's code.

#### Scenario: Volatile flag publishes data
- **WHEN** a thread on one carrier writes a field and then sets a `volatile` flag, and a thread on the other carrier spins on the flag and then reads the field
- **THEN** the reader SHALL see the written value

#### Scenario: No torn long
- **WHEN** one carrier repeatedly writes a `volatile long` field alternating between 0 and -1 while the other carrier reads it
- **THEN** every read SHALL be 0 or -1

### Requirement: Board constraints
On the ESP32 family, the carrier on core 0 SHALL let the core-0 idle task run often enough that the task watchdog never fires. On the Pico 2 W, a core paused by the core library during a flash write SHALL NOT be scanned as stopped unless it paused at a safepoint.

#### Scenario: Long computation on core 0
- **WHEN** a virtual thread on the ESP32 core-0 carrier runs a CPU-bound loop for 30 seconds
- **THEN** the board SHALL NOT reset from the task watchdog

### Requirement: Validation
The change SHALL be validated by `XtensaRunIT` on the ESP32-S3 running dual-carrier programs that cover the scenarios above, and by one dual-core example uploaded with `juno:upload` and checked with `juno:monitor` on the Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W, run long enough to expose intermittent races.

Until the pipeline's ESP32-S3 emulator can execute both cores, `XtensaRunIT` MAY use `juno.carriers=1`; hardware and a
dual-core-capable emulator SHALL omit the override for dual-carrier validation.

#### Scenario: Hardware confirmation
- **WHEN** the dual-core example runs for at least five minutes on each of the four boards
- **THEN** its serial output observed through `juno:monitor` SHALL match the expected result with no panic or reset
