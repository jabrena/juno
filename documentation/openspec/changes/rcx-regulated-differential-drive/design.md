## Context

The repository already has the main board-side contracts needed for navigation: `RegulatedMotor`, `Encoder`,
`DriveGeometry`, `DifferentialDrive`, `DifferentialDriveOdometry`, and `Pose`. The RCX API currently provides
open-loop `RcxMotor` handles, rotation-sensor configuration and reads, and an H8/300 replacement firmware that
processes motor and sensor commands. The firmware already receives fresh sensor readings through its ROM/A-D update
path, so the regulator can run on the brick without making an infrared request for every control correction.

The robot configuration in scope is two driven wheels on outputs A and C with rotation sensors on inputs 1 and 3.
The H8/300 firmware, its 16-bit arithmetic, the existing 2400-baud infrared link, the generated S-record/Java image,
and the project's zero-allocation board-side rules constrain the design.

## Goals / Non-Goals

**Goals:**

- Execute closed-loop two-wheel speed and finite-position movements inside the RCX firmware.
- Apply paired left/right targets atomically on one firmware control update.
- Expose the behavior through the existing `RegulatedMotor` contract and a reusable regulated differential-drive
  navigation type.
- Keep navigation geometry and odometry independent of RCX protocol details.
- Make stalls, timeouts, stop behavior, status, sensor polarity, and encoder units deterministic and testable.
- Preserve the existing open-loop `RcxMotor` API and existing firmware commands.

**Non-Goals:**

- Camera, line following, LiDAR, ArUco, or free-space path planning.
- A general-purpose RCX scheduler or arbitrary user firmware execution.
- Closed-loop correction performed by an Arduino over infrared.
- PI/PID control in the first increment, floating-point control arithmetic, or a claim of accurate straight-line travel
  without encoder calibration.
- Changing the current power-based `DifferentialDrive` semantics.
- Supporting more than the A/C plus input-1/input-3 configuration in the first increment.

## Decisions

### Run the control loop on the RCX

The RCX firmware SHALL own sensor sampling, target progression, controller arithmetic, motor output updates, and
completion detection. Infrared SHALL carry motion commands and status requests only. This avoids the existing remote
command latency and keeps both motors on the same control tick.

An initial cadence in the 20–50 Hz range remains a benchmark target from the planning input, not a hard hardware
assumption. The implementation SHALL measure the achievable cadence on the generated H8/300 image and document the
selected cadence. It may reuse the existing firmware timer dispatch if that is the only deterministic option.

### Use raw rotation steps in firmware and encoder-degree units at the Java boundary

The firmware SHALL regulate using the RCX rotation sensor's raw count representation to avoid repeated infrared reads
and preserve the sensor's available resolution. The Java adapter SHALL expose the existing `RegulatedMotor` tacho and
motion units in encoder-shaft degrees, documenting the quantization caused by the RCX sensor. `DriveGeometry` SHALL
remain responsible for converting encoder degrees to millimetres and differential-drive turns.

`REG_ARM` SHALL configure inputs 1 and 3 as rotation sensors rather than relying on a previous host configuration. The
firmware SHALL use the converted rotation `value` field, with inputs 1 and 3 represented internally by sensor indexes 0
and 2. One sensor revolution SHALL be treated as 16 raw steps. The regulator cycle SHALL run immediately after a fresh
analog update: refresh both sensors, snapshot both values, then execute one controller update. A missing fresh update
is a stale-sensor condition; a fresh update with insufficient progress is a stall condition.

The regulator SHALL use a dedicated monotonic 100 ms time base for trajectory progression. The implementation SHALL
verify that this time base fits the generated image and does not starve existing handlers before freezing the cadence.

### Add an RCX-specific synchronized pair behind board-independent navigation

The implementation SHALL introduce an RCX pair/controller abstraction for atomic commands and use it from a
`RegulatedDifferentialDrive`-style navigation type. The existing power-only `DifferentialDrive` SHALL remain
unchanged. A regulated single-motor handle may delegate to the pair/controller, but navigation operations SHALL submit
both wheel targets as one command rather than calling two independent infrared motor methods.

`RcxRegulatedPair` SHALL own the session, profile, motion ID, retry state, cached status, and tacho offsets. Left and
right `RcxRegulatedMotor` handles SHALL be views over that pair. A single-side rotation SHALL be encoded as a paired
command with the other side targeting its current position and remaining stationary. Two independent side rotations
shall not create competing motions. Profile settings are pair-level and affect the next motion only; conflicting
per-side settings SHALL be rejected or resolved before a motion starts.

### Use bounded proportional control with explicit motion state

Each wheel controller SHALL maintain a bounded target, measured position, commanded power, and proportional error. The
first increment SHALL use bounded proportional control only: its correction SHALL be derived from the current bounded
error and SHALL not depend on integral or derivative state. The controller SHALL apply acceleration limiting, output
saturation, a position tolerance, and a stall/timeout state using integer or fixed-point arithmetic. PI/PID control is
deferred until measured timing, quantization, and tuning evidence justify a separate change.

The first controller SHALL use signed modular error in the range `[-32767, 32767]` and reject an ambiguous displacement
of `32768`. Its bounded output SHALL follow this fixed-point form:

```text
proportionalPower = (KP_Q8 * abs(error) + 128) >> 8
power = clamp(proportionalPower, minimumStartingPower, configuredMaximumPower)
```

`KP_Q8`, minimum starting power, output deadband, and motor-power slew limit SHALL be firmware constants selected from
bench measurements; they SHALL not be added as additional wire fields in v1. `REG_PROFILE.acceleration` SHALL control
the target trajectory in sensor counts per 100 ms. A direction reversal SHALL reduce power to zero before applying the
opposite direction. A side inside tolerance SHALL be marked complete and held or floated according to the configured
braking policy.

### Use a bounded stateful protocol without changing existing opcodes

New commands SHALL use the existing framed/checksummed infrared transport and SHALL NOT reinterpret existing opcodes.
The RCX request buffer is 16 bytes and the opcode low three bits encode the request argument count; therefore every new
request SHALL contain no more than seven argument bytes. The following values are the proposed v1 registry and SHALL be
audited against the complete existing opcode space and probed on RCX hardware before they are frozen:

| Command | Candidate opcode | Request arguments |
| --- | ---: | --- |
| `REG_STATUS` | `0x61` | session ID |
| `REG_STOP` | `0x62` | session ID, motion ID |
| `REG_ARM` | `0x63` | session ID, mapping ID, configuration flags |
| `REG_PROFILE` | `0x67` | session ID, maximum speed, acceleration, tolerance, timeout |
| `REG_START` | `0x76` | session ID, motion ID, left target, right target |

The `0x08` opcode toggle SHALL remain a transport-level retry marker and SHALL NOT be used as an application
identifier. The request frame SHALL remain `55 FF 00`, followed by each byte and its complement, and a checksum equal to
the low byte of the opcode plus all argument bytes. Multi-byte values SHALL be little-endian.

The protocol SHALL use an 8-bit non-zero session ID and an 8-bit non-zero motion ID. The session ID SHALL be present in
every regulated command so delayed frames from a reset or prior session are rejected. Motion IDs SHALL not wrap within a
session; the host SHALL arm a new session before exhaustion. `REG_ARM` SHALL transition the firmware from `NOT_ARMED` to
`READY`, and SHALL be rejected while a motion is active.

`REG_ARM` SHALL use mapping ID `1` for motor A/sensor 1 on the left and motor C/sensor 3 on the right. Its flags SHALL be:

```text
bit 0: invert left motor
bit 1: invert right motor
bit 2: invert left sensor
bit 3: invert right sensor
bit 4: brake on stop/completion
bits 5-7: maximum motor power, 0-7
```

`REG_PROFILE` SHALL configure, without starting motion:

```text
session ID       u8
maximum speed    u16 raw sensor counts/second
acceleration     u16 raw sensor counts/second^2
tolerance        u8 raw sensor counts
timeout          u8 seconds
```

`REG_START` SHALL carry absolute modulo-16-bit sensor targets:

```text
session ID       u8
motion ID        u8
left target      u16
right target     u16
```

The firmware SHALL validate both targets before changing either motor, reject an ambiguous signed displacement of 32768
counts or more, stage both sides, and then start local bounded proportional control as one logical transaction. A new
motion while another is active SHALL return `BUSY`; the firmware SHALL neither queue nor silently replace it.

`REG_STOP` SHALL be qualified by the session and active motion ID. A repeated stop for the same ID SHALL be idempotent;
a stale stop SHALL NOT stop a newer motion. If an unconditional emergency stop is later required, it SHALL use a
separate explicitly named command.

`REG_STATUS` SHALL return the session ID, motion ID, state, fault bits, and both absolute positions. The states SHALL
include `NOT_ARMED`, `READY`, `RUNNING`, `COMPLETE`, `STOPPED`, `STALLED`, `TIMED_OUT`, and `FAULT`. Fault bits SHALL
identify at least left/right stall, left/right stale sensor, timeout, external override, and invalid configuration.

Every valid command SHALL receive a reply with the complemented opcode. Acknowledgement results SHALL distinguish at
least `ACCEPTED`, `DUPLICATE`, `NOT_ARMED`, `BUSY`, `INVALID_SESSION`, `INVALID_ARGUMENT`, `OUT_OF_RANGE`,
`ID_CONFLICT`, `STALE_COMMAND`, and `UNSUPPORTED`. `ACCEPTED` SHALL mean that the RCX accepted the command and began
local processing; it SHALL NOT mean that the motion completed.

Retry SHALL belong to a regulated protocol adapter above `RcxLink`, not to generic `RcxLink`. The adapter SHALL retry an
identical logical command at most twice, preserving session, motion ID, targets, and profile. An identical retry SHALL
return `DUPLICATE` without restarting motion. Reusing an ID with changed fields SHALL return `ID_CONFLICT` without
state mutation. After retry exhaustion, the adapter SHALL poll status and report `LINK_UNKNOWN` if the result cannot be
resolved; it SHALL never assume success or that a lost stop acknowledgement means the motors stopped.

Malformed transport frames—bad preamble, complement, checksum, truncation, or framing gap—SHALL be discarded without a
reply or state change. The next valid preamble SHALL begin a new frame. Correctly framed commands with an invalid length,
session, flag, range, or state SHALL receive a deterministic rejection and SHALL not partially update state.

Existing direct motor, sensor, display, sound, battery, channel-selection, and firmware-download commands SHALL retain
their current behavior. The regulated adapter SHALL serialize channel selection and regulated commands through the same
`RcxLink`.

### Regenerate the tracked firmware artifacts from one image

The firmware source remains the authority. The S-record and generated Java image SHALL be regenerated together using the
existing `RcxRemoteFirmware` workflow and SHALL not be hand-edited. Generated bytes and assembly output SHALL remain
deterministic.

### Define ownership, recovery, and safety behavior

While regulated ownership is `READY` or `RUNNING`, direct legacy commands affecting A/C or sensors 1/3 SHALL be
rejected. The remote STOP command SHALL always stop both outputs. Any other manual A/C command SHALL cancel regulation,
stop the controller, and latch `EXTERNAL_OVERRIDE`; B-only commands remain allowed. The controller SHALL never write
motor outputs after ownership has been lost.

`STALLED`, `TIMED_OUT`, `FAULT`, and `EXTERNAL_OVERRIDE` SHALL be latched terminal states. `REG_STATUS` SHALL be
read-only. A new `REG_ARM` session is the v1 recovery boundary and is accepted only while both outputs are safe; it
reconfigures the sensors, clears regulator history and faults, and returns to `READY`. A power reset returns to
`NOT_ARMED` with safe outputs.

The firmware SHALL define numerical stall progress, stall sample count, sensor freshness deadline, timeout overshoot,
completion tolerance, and maximum A/C output skew before implementation. The pair SHALL stop both outputs when either
side stalls, becomes stale, or times out.

### Keep navigation physical and calibrated

Navigation SHALL use calibrated sensor steps per motor revolution and sensor steps per wheel revolution. It SHALL keep
RCX modulo-16-bit arithmetic inside the adapter, use `long` intermediates, and reject overflow, non-zero movements that
quantize to zero, and signed displacements of 32768 steps or more.

For an in-place turn, equal physical wheel distances—not equal encoder counts—are required. With effective wheel
diameter `D`, effective track width `W`, left calibration `CL`, and right calibration `CR`, a positive counter-clockwise
turn SHALL use:

```text
leftSteps  = -round(W * angleDegrees * CL / (360 * D))
rightSteps = +round(W * angleDegrees * CR / (360 * D))
```

The calibration example SHALL measure polarity, sensor gearing, wheel steps, and effective track width rather than
assuming symmetric `1800, 1800` values.

### Keep example programs as implementation targets during planning

The specification records two example ideas for a later implementation in `juno-examples`: a calibration example for
the A/C motors and input-1/input-3 rotation sensors, and a regulated differential-drive example for calibrated travel,
in-place rotation, status polling, and safe stop. These are acceptance-oriented usage targets, not source files to add
during this planning update. Their eventual implementations SHALL follow the existing RCX example package and
board-side allocation rules.

## Risks / Trade-offs

- **[RCX timing is slower than the proposed 20–50 Hz target]** → Benchmark the generated firmware first; make the
  selected cadence explicit and test behavior at that cadence rather than assuming it.
- **[Rotation sensors provide coarse, quantized feedback]** → Regulate raw steps, use a tolerance aligned with the
  sensor resolution, and report calibration/quantization limits in the API documentation.
- **[A/C or sensor polarity may differ between robot builds]** → Make output/sensor mapping and polarity explicit in
  construction/configuration, validate them before motion, and provide a calibration procedure in the example.
- **[A lost IR acknowledgement could leave a motion active]** → Add finite-motion timeouts, explicit stop, stall
  detection, and a status/error state; do not require continuous remote corrections for a completed motion.
- **[Protocol payloads compete with the small RCX request buffer]** → Keep the first wire format fixed-width and
  bounded, split arm/profile configuration from the atomic start command, verify it against the generated image, and
  reject values outside representable ranges.
- **[A delayed frame could be accepted after an RCX reset]** → Include a session identifier in every regulated command,
  use a per-session motion identifier, and require an arm/ready transition before starting motion.
- **[A bounded-P controller may not overcome static friction]** → Measure and freeze minimum starting power, deadband,
  gain, slew limit, stall progress, and freshness thresholds before firmware implementation.
- **[The Java single-motor API conflicts with pair-owned motion]** → Keep the public `RegulatedMotor` contract intact,
  expose pair-owned state through concrete RCX types, and reject ambiguous side-specific operations.
- **[Changing the existing motor interfaces could break examples]** → Preserve `RcxMotor` and current navigation
  contracts; add the regulated adapter and regulated navigation path separately.

## Migration Plan

1. Freeze sensor mode, 100 ms timing, bounded-P formula, safety thresholds, ownership, and recovery behavior through
   tests and benchmark evidence.
2. Freeze and test the regulated wire contract, including the opcode audit, fixed-width payloads, retry/idempotency
   rules, acknowledgements, status frames, and malformed-frame behavior.
3. Add the deterministic motor/encoder plant and raw-frame fault harness before implementing firmware behavior.
4. Add firmware state and the single-wheel controller, then the coordinated A/C controller; regenerate both artifacts.
5. Add the RCX regulated adapter and pair abstraction while keeping `RcxMotor` intact.
6. Add regulated differential-drive conversion using calibrated geometry, followed by odometry integration.
7. Implement the example ideas recorded in the capability specification under `juno-examples`, then validate generated
   assembly, firmware image, protocol behavior, straight travel, turns, stalls, timeout, stop, and polarity
   calibration.

Rollback is source-level: remove the new regulated command handlers and adapter while retaining the existing generated
firmware command set. Do not archive or replace the current firmware image until the regenerated artifacts pass their
existing tests.

## Open Questions

- What measured `KP_Q8`, minimum starting power, deadband, slew limit, stall window, freshness deadline, completion
  tolerance, and maximum A/C output skew pass the bench and simulator acceptance tests?
- Can the dedicated 100 ms time base fit the image and timing budget without starving infrared, display, and sensor
  handlers?
- What physical wheel diameter, effective track width, sensor gearing, and direction polarity does calibration produce?
- Should a later protocol version add long-motion segmentation or retain the v1 signed 16-bit displacement limit?
