## ADDED Requirements

### Requirement: RCX dual-motor mapping is explicit

The regulated RCX capability SHALL support a documented two-wheel mapping of motor outputs A and C to rotation sensors
on inputs 1 and 3. The mapping SHALL include each side's direction polarity and SHALL be validated before a regulated
motion is accepted.

#### Scenario: The configured A/C pair is accepted

- **WHEN** a regulated RCX pair is configured with outputs A and C, sensors 1 and 3, and valid polarity settings
- **THEN** the firmware accepts the configuration and reports it as ready for motion

#### Scenario: An unsupported mapping is rejected

- **WHEN** a configuration refers to an unsupported output/sensor combination or an invalid polarity value
- **THEN** the firmware rejects it without changing an active motion

### Requirement: Sensor feedback is local to the RCX

The RCX firmware SHALL read the cached rotation-sensor values for both configured inputs and SHALL perform motor
control corrections locally. A regulated motion SHALL NOT require one infrared round trip per correction cycle.
`REG_ARM` SHALL configure inputs 1 and 3 as rotation sensors, using internal sensor indexes 0 and 2 and the converted
rotation `value` field. One sensor revolution SHALL equal 16 raw sensor steps. The regulator SHALL run after a fresh
analog update has refreshed and snapshotted both values. A missed fresh update SHALL be reported as stale-sensor
behavior, distinct from a stall where fresh samples arrive without sufficient progress.

#### Scenario: Both wheel controllers receive feedback while moving

- **WHEN** a configured pair is executing a regulated motion and both rotation sensors produce new counts
- **THEN** the firmware updates both controllers and applies both motor outputs in the same control cycle

#### Scenario: Infrared traffic is absent during an active finite motion

- **WHEN** a finite regulated motion has been accepted and no new infrared command arrives before completion
- **THEN** the RCX continues local control, subject to its motion timeout and stall-safety rules

#### Scenario: The regulator receives a fresh sensor update

- **WHEN** both configured rotation sensors are refreshed by the analog update
- **THEN** the firmware snapshots both positions and performs one controller update without requiring an infrared request

### Requirement: Paired motion is atomic

The protocol SHALL provide one atomic `REG_START` command for a coordinated left/right motion. Speed, acceleration,
position tolerance, and timeout SHALL be configured beforehand with `REG_PROFILE`; the start command SHALL carry the
session ID, motion ID, and both absolute left/right targets. The firmware SHALL apply both targets from the same
accepted command and SHALL expose one combined motion status.

#### Scenario: Straight travel starts both wheels together

- **WHEN** navigation submits equal left and right encoder targets in one paired command
- **THEN** both wheel controllers stage their targets, enter one coordinated motion, and update A and C consecutively
  rather than accepting two independent starts

#### Scenario: An in-place turn uses opposite targets

- **WHEN** navigation submits equal-magnitude opposite-sign targets for a turn
- **THEN** the left and right controllers run in opposite directions using independently calculated targets that produce
  equal physical wheel distances, and the pair completes only after both targets meet their tolerances

#### Scenario: One side reaches tolerance first

- **WHEN** one wheel reaches its configured tolerance before the other wheel
- **THEN** that side is held or floated according to the braking policy while the other side continues, and the pair
  remains running until both sides complete

#### Scenario: A zero-distance motion is accepted

- **WHEN** both absolute targets equal the current positions within tolerance
- **THEN** the firmware reports `COMPLETE` without energizing either motor

### Requirement: Closed-loop control uses bounded proportional regulation

Each wheel controller SHALL compare measured rotation against its target trajectory and apply bounded proportional
correction using the current error only. The first increment SHALL NOT use integral or derivative state. It SHALL limit
acceleration, clamp motor output to the RCX range, use integer or fixed-point arithmetic, and apply an explicit position
tolerance consistent with the configured sensor resolution. PI/PID control is deferred until a later change provides
measured timing, quantization, tuning, anti-windup, and derivative-filtering requirements.
The controller SHALL use signed modular error in `[-32767, 32767]` and reject `32768`. Its v1 output SHALL use:

```text
proportionalPower = (KP_Q8 * abs(error) + 128) >> 8
power = clamp(proportionalPower, minimumStartingPower, configuredMaximumPower)
```

`KP_Q8`, minimum starting power, output deadband, and motor-power slew limit SHALL be firmware constants selected from
bench measurements. `REG_PROFILE.acceleration` SHALL control target trajectory progression in sensor counts per 100 ms;
a reversal SHALL reduce power to zero before changing direction. The regulator SHALL use a dedicated monotonic 100 ms
time base, subject to image-size and timing-budget verification.

#### Scenario: A wheel falls behind its target

- **WHEN** measured progress is lower than the target trajectory
- **THEN** the controller increases power within its configured acceleration and output bounds

#### Scenario: A bounded proportional correction is calculated

- **WHEN** a wheel has a non-zero signed modular error outside tolerance
- **THEN** the controller applies the fixed-point bounded-P formula, minimum starting power, configured maximum power,
  and power-slew limit without integral or derivative state

#### Scenario: A wheel reaches its tolerance

- **WHEN** a wheel's measured position is within its configured target tolerance
- **THEN** that wheel is considered complete and its output is not driven beyond the completion policy

#### Scenario: A motion is stopped and restarted

- **WHEN** a regulated motion is stopped and a new motion is later accepted
- **THEN** the proportional controller starts from the new measured position without any accumulated integral or
  derivative state from the previous motion

### Requirement: Regulated motor semantics match the existing contract

The RCX regulated adapter SHALL implement the existing `RegulatedMotor` semantics for speed, acceleration, relative
rotation, absolute rotation, tacho count, reset, completion waiting, movement state, and stall reporting. It SHALL
preserve encoder-degree units at the Java boundary and document RCX sensor quantization. The adapter SHALL allocate
all required state during setup and SHALL not allocate during polling or motion commands.
The pair SHALL own the profile, motion, retry, cached status, and tacho offsets; left and right motor handles SHALL be
views over that pair. Profile changes SHALL affect the next motion only. A single-side rotation SHALL use a paired
command with the other side targeting its current position and remaining stationary. The existing `RegulatedMotor`
interface SHALL not gain new abstract methods; RCX-specific state and error accessors belong on concrete RCX types.

#### Scenario: A relative rotation is executed

- **WHEN** the caller invokes `rotate(degrees, false)` on an RCX regulated motor
- **THEN** the adapter submits a finite target relative to the current tacho count and returns only after completion or
  a reported error state

#### Scenario: An asynchronous rotation is executed

- **WHEN** the caller invokes `rotate(degrees, true)`
- **THEN** the adapter returns after the paired command is accepted while the RCX continues regulating the motion

#### Scenario: A stalled motor is reported

- **WHEN** the controller detects that commanded output produces no sufficient sensor progress before the stall limit
- **THEN** the adapter reports `isStalled()` and the pair enters a safe stopped/error state

#### Scenario: The caller resets a tacho count during motion

- **WHEN** `resetTachoCount()` is invoked while the pair is moving
- **THEN** the adapter rejects the reset and does not clear the firmware sensor counter

#### Scenario: The caller resets a tacho count while idle

- **WHEN** `resetTachoCount()` is invoked while no motion is active
- **THEN** the adapter changes only its Java-side zero offset and preserves the firmware sensor reference

#### Scenario: A terminal link outcome is observed

- **WHEN** status retries cannot determine whether a motion completed
- **THEN** the adapter reports `LINK_UNKNOWN` distinctly from `STALLED`, `TIMED_OUT`, or successful completion

### Requirement: Regulated differential-drive navigation converts geometry to paired targets

The navigation integration SHALL provide a regulated differential-drive path that uses `DriveGeometry` to convert
millimetre travel and robot-heading rotations into left/right encoder targets. It SHALL support independent left and
right encoder ratios and SHALL not modify the existing power-based `DifferentialDrive` behavior.
Navigation SHALL distinguish sensor steps per motor revolution from sensor steps per wheel revolution. It SHALL use
checked `long` intermediates, reject arithmetic overflow and non-zero movements that quantize to zero, and reject signed
displacements of 32768 sensor steps or more. RCX modulo-16-bit target arithmetic SHALL remain in the RCX adapter.

#### Scenario: Straight travel is converted symmetrically

- **WHEN** the caller requests a positive travel distance with calibrated geometry
- **THEN** the navigation layer submits same-direction targets scaled by each side's encoder ratio

#### Scenario: Robot rotation is converted from track width

- **WHEN** the caller requests a positive or negative robot rotation
- **THEN** the navigation layer submits opposite-direction wheel targets whose independently calculated magnitudes
  produce equal physical wheel distances, using the effective track width and each side's encoder ratio

#### Scenario: Invalid geometry is rejected

- **WHEN** wheel diameter, track width, or either encoder ratio is non-positive or exceeds the supported arithmetic
  range
- **THEN** the navigation layer rejects the geometry before sending a motion command

#### Scenario: A nonzero movement quantizes to zero

- **WHEN** a requested movement is nonzero but converts to zero raw sensor steps
- **THEN** navigation rejects it as unrepresentable instead of silently reporting completion

#### Scenario: A turn uses asymmetric gearing

- **WHEN** the left and right wheels have different calibrated sensor steps per wheel revolution
- **THEN** the turn uses different encoder target magnitudes while preserving equal physical wheel arc lengths

### Requirement: Motion status and safety behavior are explicit

The firmware protocol SHALL expose running, complete, stopped, stalled, timed-out, and fault states. Invalid-command,
invalid-configuration, and invalid-session conditions SHALL be acknowledgement results rather than motion states. An
explicit stop command SHALL stop both outputs. A finite motion SHALL stop safely when
its timeout expires or a stall is detected, and status polling SHALL not clear the error until the caller acknowledges
or resets the controller according to the documented API.
`STALLED`, `TIMED_OUT`, `FAULT`, and `EXTERNAL_OVERRIDE` SHALL remain latched terminal states. A new `REG_ARM` session
is the v1 recovery operation and SHALL be accepted only when both outputs are safe; it SHALL reconfigure the sensors,
clear regulator history and faults, and return to `READY`. A power reset SHALL return to `NOT_ARMED` with safe outputs.
Direct legacy commands affecting A/C or sensors 1/3 SHALL be rejected while regulated ownership is active. Remote STOP
SHALL always stop both outputs; other manual A/C commands SHALL cancel regulation and record `EXTERNAL_OVERRIDE`.

#### Scenario: The caller stops an active motion

- **WHEN** the caller sends the paired stop command
- **THEN** both outputs stop according to the configured braking policy and status becomes stopped

#### Scenario: A motion exceeds its timeout

- **WHEN** an active finite motion exceeds its computed or configured maximum duration
- **THEN** the firmware stops both outputs and reports timed out

#### Scenario: The caller polls completion

- **WHEN** the caller requests status after both wheel targets reach tolerance
- **THEN** the firmware reports complete with both final positions and no stale running flag

#### Scenario: A wheel stalls while fresh samples continue

- **WHEN** a wheel remains outside tolerance, receives sufficient commanded power, and makes insufficient progress for
  the configured stall window
- **THEN** the firmware stops both outputs and latches `STALLED`

#### Scenario: Sensor freshness expires

- **WHEN** no fresh analog update arrives before the sensor freshness deadline
- **THEN** the firmware stops both outputs, reports the stale-sensor fault, and does not classify the condition as a
  motor stall

#### Scenario: A terminal fault is recovered

- **WHEN** a new `REG_ARM` session is received while both outputs are safe after a terminal fault
- **THEN** the firmware clears the prior fault and motion history and returns to `READY`

#### Scenario: A legacy A/C command arrives during regulation

- **WHEN** a direct legacy or remote-control command would change motor A/C or sensors 1/3 while ownership is active
- **THEN** the command is rejected or the controller is explicitly cancelled with `EXTERNAL_OVERRIDE`, and the regulator
  never overwrites the resulting manual state

### Requirement: The regulated wire protocol is bounded and versioned

The regulated protocol SHALL use the existing RCX frame format: `55 FF 00`, each opcode/argument/checksum byte followed
by its complement, and a checksum equal to the low byte of the opcode plus all argument bytes. Multi-byte values SHALL
be little-endian. New request opcodes SHALL encode their argument count in the low three bits and SHALL contain no more
than seven argument bytes. The proposed v1 opcode registry SHALL be audited against existing commands before
implementation:

| Command | Candidate opcode | Request arguments |
| --- | ---: | --- |
| `REG_STATUS` | `0x61` | session ID |
| `REG_STOP` | `0x62` | session ID, motion ID |
| `REG_ARM` | `0x63` | session ID, mapping ID, configuration flags |
| `REG_PROFILE` | `0x67` | session ID, maximum speed, acceleration, tolerance, timeout |
| `REG_START` | `0x76` | session ID, motion ID, left target, right target |
| `REG_BATTERY` | `0x70` | none; reply: battery millivolts (low, high), no session needed |

The `0x08` opcode toggle SHALL be treated only as a transport retry marker. It SHALL NOT identify a motion or replace an
application sequence identifier. Existing open-loop, sensor, display, sound, battery, channel-selection, and firmware
download opcodes SHALL remain unchanged.

#### Scenario: A valid regulated command is framed

- **WHEN** a regulated command is transmitted
- **THEN** it uses the existing preamble, byte complements, bounded argument count, little-endian word encoding, and
  checksum rules

#### Scenario: An oversized regulated request is proposed

- **WHEN** a command would require more than seven argument bytes
- **THEN** the design splits configuration from motion start instead of extending the RCX request format

### Requirement: Session and motion identity prevent stale commands

The protocol SHALL use non-zero 8-bit session and motion IDs. The session ID SHALL be included in every regulated
command. `REG_ARM` SHALL establish the session and validate mapping ID `1` as motor A/sensor 1 on the left and motor
C/sensor 3 on the right. Motion IDs SHALL not wrap within a session; the host SHALL establish a new session before
exhaustion. The firmware SHALL begin in `NOT_ARMED` and SHALL accept `REG_START` only in `READY`.

#### Scenario: A command from an old session arrives

- **WHEN** a regulated command carries a session ID different from the armed session
- **THEN** the firmware returns `INVALID_SESSION` and leaves motion state unchanged

#### Scenario: A new session is armed safely

- **WHEN** `REG_ARM` is received while no motion is active with a valid mapping and flags
- **THEN** the firmware clears the prior motion history and enters `READY`

### Requirement: Profile and paired start fields have exact units

`REG_ARM` SHALL encode left/right motor and sensor polarity, brake policy, and maximum motor power from 0 through 7
in its configuration flags. `REG_PROFILE` SHALL encode maximum speed as unsigned 16-bit raw sensor counts per second,
acceleration as unsigned 16-bit raw sensor counts per second squared, tolerance as unsigned 8-bit raw sensor counts,
and timeout as unsigned 8-bit seconds. `REG_START` SHALL encode absolute unsigned 16-bit modulo sensor positions for
both wheels. Java SHALL perform checked conversion from relative encoder-degree movement and SHALL reject an ambiguous
signed displacement of 32768 counts or more.

The `REG_ARM` configuration flag bits SHALL be:

```text
bit 0: invert left motor
bit 1: invert right motor
bit 2: invert left sensor
bit 3: invert right sensor
bit 4: brake on stop/completion
bits 5-7: maximum motor power, 0-7
```

The status fault bits SHALL be:

```text
bit 0: left stall
bit 1: right stall
bit 2: left sensor stale
bit 3: right sensor stale
bit 4: timeout
bit 5: external motor override
bit 6: invalid configuration
bit 7: reserved and zero
```

#### Scenario: Profile values are outside representable limits

- **WHEN** speed, acceleration, tolerance, or timeout cannot be represented by the defined fields or supported fixed
  point arithmetic
- **THEN** the firmware rejects the profile without changing the active profile

#### Scenario: A paired target is accepted

- **WHEN** a valid `REG_START` contains both representable targets while the firmware is `READY`
- **THEN** the firmware stages both targets and begins one paired local-control transaction

### Requirement: Acknowledgement, retry, and completion semantics are distinct

Every valid regulated command SHALL receive a reply with the complemented opcode. Replies SHALL distinguish at least
`ACCEPTED`, `DUPLICATE`, `NOT_ARMED`, `BUSY`, `INVALID_SESSION`, `INVALID_ARGUMENT`, `OUT_OF_RANGE`, `ID_CONFLICT`,
`STALE_COMMAND`, and `UNSUPPORTED`. `ACCEPTED` SHALL mean accepted for local processing, not completed. The regulated
protocol adapter SHALL retry an identical logical command at most twice, preserving its session, motion ID, and payload.
The same motion ID and identical payload SHALL be idempotent; the same motion ID with different fields SHALL return
`ID_CONFLICT` without state mutation. After retry exhaustion, the adapter SHALL resolve the result through status or
report `LINK_UNKNOWN`; it SHALL not assume success. Reply payloads SHALL have these fixed layouts:

```text
REG_ARM:     result u8, protocol version u8, mapping ID u8
REG_PROFILE: result u8
REG_START:   result u8, session ID u8, motion ID u8
REG_BATTERY: millivolts u16 (no arguments; the battery needs no session)
REG_STOP:    result u8, session ID u8, motion ID u8
REG_STATUS:  session ID u8, motion ID u8, state u8, fault bits u8,
             left position u16, right position u16
```

The status reply SHALL therefore contain eight argument bytes. The state values SHALL be
`NOT_ARMED=0`, `READY=1`, `RUNNING=2`, `COMPLETE=3`, `STOPPED=4`, `STALLED=5`, `TIMED_OUT=6`, and `FAULT=7`.

#### Scenario: A start acknowledgement is lost

- **WHEN** the adapter retries the same start command after a timeout
- **THEN** the firmware returns `DUPLICATE` or the original result and SHALL NOT restart the motion

#### Scenario: A different command reuses a motion ID

- **WHEN** a command reuses an existing motion ID with different targets or profile state
- **THEN** the firmware returns `ID_CONFLICT` and leaves the active motion unchanged

#### Scenario: Completion is polled

- **WHEN** the adapter requests status after an accepted start
- **THEN** the status includes session ID, motion ID, state, fault bits, and both final/current positions, allowing the
  adapter to distinguish completion from acknowledgement and link uncertainty

### Requirement: Malformed frames are harmless and semantic errors are deterministic

Frames with a bad preamble, byte complement, checksum, truncation, or framing gap SHALL be discarded without a reply or
firmware state change. The next valid preamble SHALL begin a new frame. Correctly framed commands with an invalid
argument count, session, flag, range, or state SHALL receive a deterministic rejection and SHALL not partially update
configuration, targets, or motor outputs.

#### Scenario: A command has an unexpected argument count

- **WHEN** a known regulated opcode is received with fewer or more arguments than its fixed layout
- **THEN** the firmware rejects it deterministically and does not dispatch a partial command

#### Scenario: A corrupt frame is received

- **WHEN** any transport integrity check fails
- **THEN** the firmware discards the frame, does not change state, and waits for the next valid preamble

#### Scenario: A semantically invalid frame is received

- **WHEN** a frame is validly encoded but contains an invalid field or state transition
- **THEN** the firmware returns a rejection result and leaves the previous state intact

### Requirement: Existing RCX behavior remains compatible

The change SHALL preserve existing open-loop `RcxMotor` calls, existing sensor reads, existing firmware opcodes, and
the generated S-record/Java image workflow. New regulated commands SHALL be additive and SHALL not silently reinterpret
an existing command. While regulated ownership is active, legacy A/C and input-1/input-3 commands SHALL be rejected;
remote STOP SHALL remain an unconditional safety override, and B-only commands MAY continue when they cannot affect the
regulated pair. The regulated adapter SHALL serialize all operations through one owner of the shared `RcxLink`.

#### Scenario: An existing open-loop example is run

- **WHEN** an existing example uses `RcxMotor` to set power and direction
- **THEN** it continues to produce the same protocol behavior and generated output as before the change

#### Scenario: The generated artifacts are regenerated

- **WHEN** the firmware generator writes the S-record and Java image
- **THEN** both artifacts are derived from the same deterministic firmware image and their existing consistency tests
  pass

#### Scenario: An existing command targets an owned resource

- **WHEN** a legacy command would change motor A/C or clear/configure sensor input 1/3 during regulated ownership
- **THEN** the firmware rejects it or records an explicit `EXTERNAL_OVERRIDE`, and the regulated controller does not
  continue writing competing outputs

### Requirement: The capability is validated without requiring continuous hardware access

The implementation SHALL provide deterministic unit and generated-firmware tests for protocol encoding, mapping,
controller bounds, target conversion, completion, stop, stall, timeout, and compatibility. Hardware validation MAY add
an RCX example, but a successful upload alone SHALL NOT be treated as proof of regulated navigation behavior.
The test plan SHALL include a raw-frame transport harness, a deterministic motor/encoder plant, dropped/duplicated/
delayed/corrupt reply injection, reset/power-loss simulation, and assertions over both firmware state and motor outputs.
Release evidence SHALL include image checksum, selected cadence, profile values, raw status frames, completion error, stop
latency, stall/timeout behavior, reset behavior, and legacy-command arbitration. The generated image SHALL retain the
8 KiB maximum, entry/unlock constraints, explicit RAM non-overlap, deterministic S-record/image equality, and a previous
known-good reflash path.

#### Scenario: Protocol and navigation tests run offline

- **WHEN** the normal Maven test suite runs without an RCX connected
- **THEN** it verifies command encoding, geometry conversion, controller state transitions, and generated-image
  determinism

#### Scenario: Hardware behavior is validated

- **WHEN** the regulated example is deployed to a physical RCX setup
- **THEN** validation includes serial/protocol status evidence and observed straight travel, turns, stop, stall, and
  timeout behavior rather than only successful firmware download, with the recorded image checksum, raw status frames,
  timing, and recovery evidence

#### Scenario: A simulated transport fault is injected

- **WHEN** a malformed, truncated, dropped, duplicated, delayed, or corrupted frame/reply is injected
- **THEN** the test verifies deterministic adapter state, safe firmware outputs, and no false completion

## Example Ideas for Later `juno-examples` Implementation

These examples are planning targets for the implementation phase. This OpenSpec update records their intended usage;
it does not add Java source files to `juno-examples` yet.

### `RcxRegulatedMotorCalibration`

An example for calibrating the proposed A/C plus input-1/input-3 wiring. It should create the infrared link and
`RemoteRcx` once, configure one regulated motor per side, reset both tacho counts, perform a short bounded movement,
and print both sides' position/status results. It should make direction polarity, sensor gearing, wheel-side assignment,
raw sensor steps, sensor steps per motor revolution, sensor steps per wheel revolution, quantization, and effective track
width easy to verify before navigation is attempted. It SHALL treat geometry values such as `1800, 1800` as illustrative
inputs only and SHALL print calibrated values for later configuration.

### `RcxRegulatedDifferentialDrive`

An end-to-end navigation example based on the conversation's proposed usage shape:

```java
DriveGeometry geometry = new DriveGeometry(81, 140, 1800, 1800);
RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, geometry);
drive.setLinearSpeedMillimetresPerSecond(150);
drive.travelMillimetres(500);
drive.turnDegrees(90);
drive.stop();
```

The eventual example should use the final public API names, configure geometry once, demonstrate finite travel and an
in-place rotation, report completion/error status, and explicitly stop both sides. It SHALL use calibrated geometry,
document positive turns as counter-clockwise, support asymmetric encoder targets for turns, poll until `COMPLETE`, and
handle `STALLED`, `TIMED_OUT`, and `LINK_UNKNOWN`. The values above are illustrative calibration inputs from the
planning conversation, not fixed hardware defaults.

### Example implementation constraints

- Place the eventual classes in the existing RCX examples package under `juno-examples`.
- Allocate the link, motors, pair, geometry, and navigation object during setup.
- Keep repeated status polling and control loops free of avoidable allocations.
- Compile through the existing example workflow before considering physical deployment.
- Treat physical upload and observed RCX behavior as separate validation work requiring the repository's normal approval.
