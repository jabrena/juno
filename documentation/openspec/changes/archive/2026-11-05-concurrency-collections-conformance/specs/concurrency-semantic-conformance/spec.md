# concurrency-semantic-conformance Specification

## ADDED Requirements

### Requirement: Concurrency semantics are catalogued

The catalog SHALL define a `concurrency` scope listing JLS §§8.3.1.4, 8.4.3.6, 14.19, 17.1, 17.2, 17.3, 17.4, 17.5,
17.6, and 17.7. Each section SHALL be covered by an area that references a program and declares Thumb-2, AVR, and Xtensa
with a status, or skipped with a reason. The `controlflow` scope SHALL keep its `Other category: concurrency/` pointer
for §14.19.

#### Scenario: Catalog validation loads the concurrency areas

- **WHEN** the conformance catalog is validated
- **THEN** it finds the concurrency scope, every listed section classified by an area, and all three backend claims for
  each area

### Requirement: Monitors, wait sets, and thread lifecycle match OpenJDK

The `programs/concurrency/` fixtures SHALL produce deterministic serial output that equals the OpenJDK transcript:
`MonitorsAndWaitSets` covers `synchronized` blocks and methods and `wait`/`notifyAll` under a monitor; `ThreadLifecycle`
covers `Thread.start`, `join`, `sleep`, and `yield`, and a final field published through a started thread;
`VisibilityAndAtomics` covers a `volatile` flag polled by a worker and the `AtomicInteger`, `AtomicBoolean`, and
`AtomicLong` operations; `WordTearing` covers two threads that each write their own `byte` or `short` field of one object
without a lock. No fixture starts more than two worker threads beside `main`.

#### Scenario: A concurrency fixture runs on a required backend

- **WHEN** a concurrency fixture runs under the Thumb-2, AVR, or Xtensa conformance runner
- **THEN** its serial transcript equals the OpenJDK transcript, and running it twice produces the same transcript

#### Scenario: A worker thread result is read after join

- **WHEN** a fixture needs a value a worker computed
- **THEN** it reads the value after `join`, and prints no value that depends on how often a thread was preempted

### Requirement: Non-atomic long and double access is claimed only where the subset claims it

`LongFieldPublication` SHALL be a separate program whose `@Board` annotation selects only the UNO R4 WiFi and the UNO Q,
and whose area declares AVR and Xtensa as `UNSUPPORTED`, because the Java subset page makes the whole-64-bit-access
claim for the Arm boards only. Its serial transcript SHALL equal the OpenJDK transcript on the Arm boards.

#### Scenario: A long field read by another thread is never torn on Arm

- **WHEN** a worker alternates a `long` field between two values and `main` reads it after the worker is joined and during
  the run
- **THEN** every value `main` reads is one of the two values, and the printed count of torn reads is zero

#### Scenario: The fixture does not run on the AVR or Xtensa boards

- **WHEN** the conformance runners select boards for `LongFieldPublication`
- **THEN** the program's `@Board` annotation excludes the AVR and Xtensa boards

### Requirement: Scope boundary is recorded

The catalog SHALL NOT cite a `java.util.concurrent` coordination class (`CountDownLatch`, `Semaphore`, `CyclicBarrier`,
`Phaser`, `ReadWriteLock`, `Exchanger`, `BlockingQueue`), `StructuredTaskScope`, or `ScopedValue` as a covered JLS
section, because the JLS defines no behavior for them. The design records each of these as out of scope with its reason.

#### Scenario: A library class is not claimed as a JLS section

- **WHEN** the catalog is reviewed against the design's scope table
- **THEN** no area cites a JLS section for a `java.util.concurrent` coordination class or a preview API
