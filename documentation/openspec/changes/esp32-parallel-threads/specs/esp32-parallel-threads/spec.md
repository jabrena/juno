## ADDED Requirements

### Requirement: Opt-in two-core execution

Juno SHALL run up to `juno.cores` Juno threads executing Java at the same time on a core runtime whose
`parallelCores()` is at least that value, and SHALL reject a `juno.cores` value above the capability with a
`CompileException`.

#### Scenario: Two cores on the Nano ESP32

- **WHEN** a program with two worker threads is compiled for the Nano ESP32 with `-Djuno.cores=2`
- **THEN** the shim uses the two-core thread port and two run tokens

#### Scenario: Flag rejected on a single-core board

- **WHEN** a program is compiled for UNO R4 WiFi with `-Djuno.cores=2`
- **THEN** compilation fails with a `CompileException` that names the board's core count

### Requirement: Default output is unchanged

With `juno.cores` at its default of 1, the generated `.S`, shim and report SHALL be byte-identical to the output
before this change, for every board and example.

#### Scenario: Default build of an ESP32 threaded example

- **WHEN** an existing threaded example is compiled for the Nano ESP32 without `-Djuno.cores`
- **THEN** its output is byte-identical to the baseline

### Requirement: Volatile accesses follow the Java Memory Model across cores

When cores > 1, a read of a `volatile` field SHALL have acquire semantics, and a write SHALL have release semantics
followed by a StoreLoad barrier. Reads and writes of `volatile long` and `volatile double` SHALL be atomic.

#### Scenario: Volatile lowering in the generated assembly

- **WHEN** a class with a `volatile int` field is compiled for the Nano ESP32 with `-Djuno.cores=2`
- **THEN** reads of the field use `l32ai`, and writes use `s32ri` followed by `memw`

#### Scenario: Store buffering is forbidden on hardware

- **WHEN** the volatile store-buffering litmus program runs 20,000 iterations on the Nano ESP32 and the Cardputer
  with `-Djuno.cores=2`
- **THEN** the serial output contains `SB FORBIDDEN 0`

#### Scenario: Volatile long never tears

- **WHEN** one thread alternates a `volatile long` between 0 and -1L while another thread reads it 20,000 times
- **THEN** every value read is 0 or -1L

### Requirement: Atomics and monitors are linearizable across cores

When cores > 1, `AtomicInteger`, `AtomicBoolean` and `AtomicLong` operations SHALL be atomic and sequentially
consistent, and `synchronized` SHALL give mutual exclusion and happens-before between an unlock and a later lock of
the same monitor.

#### Scenario: No lost updates

- **WHEN** two threads on different cores each call `incrementAndGet` 10,000 times on one `AtomicInteger`, and
  separately each increment a counter 10,000 times inside `synchronized`
- **THEN** both totals are 20,000

#### Scenario: Wait and notify do not lose wake-ups

- **WHEN** two threads exchange 1,000 rounds of `wait`/`notify` on one monitor
- **THEN** every round completes in order and no watchdog timeout fires

### Requirement: Final fields and thread lifecycle publish safely

When cores > 1, a constructor of a class with `final` fields SHALL end with a barrier, so that another thread that
sees the reference also sees the final fields. `Thread.start`, `join` and interrupt SHALL establish the JLS
happens-before edges.

#### Scenario: Racy publication of a final field

- **WHEN** one thread publishes an object with `final int x = 42` through a plain field, and another thread reads it
- **THEN** whenever the reference is non-null, `x` reads 42

### Requirement: The collector stops every running thread

When cores > 1, a collection SHALL start only after every other thread running Java has parked at a safepoint,
or is blocked, waiting on I/O or inside a vendor call with its stack pointer published and its register windows
spilled. It SHALL panic with a diagnostic if that does not happen within 50 ms.

#### Scenario: Allocation-heavy threads on both cores

- **WHEN** two threads on different cores allocate and drop objects in a loop until many collections have run
- **THEN** no object is corrupted, and the program's checksum matches the OpenJDK result

### Requirement: The litmus suite can detect races

The litmus suite SHALL include negative controls that use plain fields and counters. A hardware run SHALL count as
valid only if every negative control reports at least one relaxed outcome.

#### Scenario: Plain-field controls show races

- **WHEN** the plain store-buffering control and the plain-counter control run on hardware with `-Djuno.cores=2`
- **THEN** the store-buffering control reports a `0,0` count above 0, and the counter total is below 20,000
