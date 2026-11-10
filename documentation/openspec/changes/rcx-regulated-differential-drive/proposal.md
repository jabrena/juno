## Why

Juno can currently command RCX motor power and read rotation sensors, but it cannot ask the RCX to regulate two
wheel motors locally. A closed-loop controller in the RCX firmware would make infrared communication a high-level
command channel instead of a timing-sensitive motor-control loop, giving the existing differential-drive navigation
API a reliable RCX backend.

## What Changes

- Add an RCX firmware motion controller for two independently regulated motors, initially outputs A and C.
- Use rotation sensors on RCX inputs 1 and 3 as closed-loop feedback.
- Add a bounded, stateful regulated-motion protocol over the existing RCX frame format: arm, profile, atomic start,
  stop, and status.
- Connect the existing `RegulatedMotor` contract and differential-drive navigation types to an RCX implementation.
- Preserve the existing open-loop `RcxMotor` behavior and existing infrared command compatibility.
- Add deterministic unit, generated-firmware, protocol, and navigation tests, plus planning examples that can later be
  implemented under `juno-examples`.
- Use bounded proportional control with integer/fixed-point arithmetic in the first increment; defer PI/PID until
  timing, quantization, and tuning evidence justify it.
- Make safety behavior explicit for stalls, stale commands, and unsupported configurations.
- Make protocol framing, opcodes, field widths, units, identifiers, retries, acknowledgements, and malformed-frame
  behavior explicit before implementation.
- Make the remaining control, API, navigation, safety, and release contracts measurable before implementation: sensor
  mode and timing, bounded-P formula, pair ownership, calibrated geometry, fault recovery, simulator evidence, and
  firmware/image budgets.

## Capabilities

### New Capabilities

- `rcx-regulated-differential-drive`: Closed-loop dual-motor RCX firmware control and its reusable integration with
  Juno's differential-drive navigation API.

### Modified Capabilities

None. The repository has no existing root-level OpenSpec capability specification for RCX navigation; this change
introduces the first contract for it. The existing `RegulatedMotor`, `DriveGeometry`, `DifferentialDrive`, and
`DifferentialDriveOdometry` APIs are implementation constraints to preserve or extend deliberately, not requirements to
recreate.

## Impact

- `juno-third-parties/juno-rcx-firmware`: H8/300 firmware state, control loop, sensor sampling, motor regulation,
  protocol handlers, generated S-record, and generated Java image.
- `juno-api`: RCX regulated-motor adapter, synchronized-pair/protocol surface, and navigation integration while keeping
  board-independent navigation types reusable.
- `juno-api` and firmware tests: protocol encoding, controller behavior, deterministic assembly/image output, sensor
  feedback, status, timeout, stall, and coordinated-motion coverage.
- `juno-examples`: future calibration and regulated differential-drive examples derived from the example ideas in the
  capability specification. This planning change does not add those Java example classes yet.
- No new Maven dependency or external runtime service is required.

## Source and Derivation

- **Authoritative planning input:** the sanitized RCX/local-navigation plan summarized in the preceding user-approved
  conversation, derived from the linked discussion about RCX dual-motor regulation and local navigation.
- **Protocol review input:** the user-requested expert-panel review of the current `RcxLink` and H8/300 constraints,
  which selected a bounded stateful protocol and identified the required wire-level edge cases.
- **Requirements 2–8 review input:** the follow-up expert-panel review covering H8/300 control, Java zero-allocation
  semantics, navigation calibration, and verification/release safety. Its conclusions are recorded in `design.md`,
  `spec.md`, and `tasks.md`; the change remains planning-only until those measurable gates are resolved.
- **Repository constraints:** current Juno APIs and firmware implementation inspected during proposal creation,
  including the existing `RegulatedMotor`, `DriveGeometry`, `DifferentialDrive`, `RcxMotor`, `RcxRotationSensor`,
  `RcxRemoteFirmware`, `RcxMotorCommands`, and `RcxSensorCommands` types.
- **Derivation direction:** planning summary plus current repository constraints → this OpenSpec proposal; this change
  does not synchronize or rewrite the source conversation or existing code.
