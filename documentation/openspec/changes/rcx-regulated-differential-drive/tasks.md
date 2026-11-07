## 1. Establish the RCX control and protocol contract

- [ ] 1.1 Audit and probe the proposed regulated opcode registry (`0x61`, `0x62`, `0x63`, `0x67`, `0x76`) against existing Juno, LEGO, channel, and firmware-download commands before freezing it.
- [ ] 1.2 Confirm the A/C plus input-1/input-3 mapping, encoder polarity, raw-step units, Java encoder-degree conversion, and representable unsigned field ranges.
- [ ] 1.3 Measure the H8/300 timer/control-loop cadence available alongside infrared, sensor, and display handlers; record the selected cadence and controller timing assumptions.
- [ ] 1.4 Implement the fixed-width wire contract: session and motion IDs, arm/profile/start/stop/status payloads, little-endian words, complement/checksum framing, and exact acknowledgement/status values.
- [x] 1.5 Define adapter retry limits and idempotency: identical logical commands may be retried at most twice, duplicate starts must not restart motion, and unresolved outcomes become `LINK_UNKNOWN`.
- [ ] 1.6 Define the exact rotation-sensor mode/field, 16-step resolution, 100 ms trajectory time base, fixed-point `KP_Q8` formula, minimum starting power, deadband, slew limit, stall window, freshness deadline, completion tolerance, timeout overshoot, and A/C output-skew bound.
- [x] 1.7 Add raw-frame codec tests and golden vectors for valid commands, toggle variants, bounds, checksums, acknowledgements, and status decoding.
- [ ] 1.8 Add malformed-frame and semantic-rejection tests for bad preambles, complements, checksums, truncation, wrong lengths, invalid sessions, flags, ranges, stale IDs, and conflicting duplicates.
- [x] 1.9 Add navigation conversion tests for 16-step quantization, zero-step rejection, asymmetric gearing, positive/negative turns, 16-bit wraparound, 32767/32768 boundaries, and checked overflow.

## 2. Implement the RCX firmware regulator

- [x] 2.1 Add firmware state for the armed session, last/current motion ID, configured profile, target positions, measured counts, controller state, and error status.
- [x] 2.2 Implement `NOT_ARMED` → `READY` → `RUNNING` → terminal-state transitions and reject stale sessions, stale IDs, and conflicting duplicate commands without state mutation.
- [x] 2.3 Implement exact `REG_ARM`, `REG_PROFILE`, `REG_START`, `REG_STOP`, and `REG_STATUS` dispatch/reply behavior while preserving all existing opcodes.
- [x] 2.4 Configure inputs 1/3 as rotation sensors, read the converted `value` field through internal indexes 0/2, snapshot both values after each fresh analog update, and keep stale-sensor and stall detection distinct.
- [ ] 2.5 Add a dedicated monotonic 100 ms regulator time base without starving infrared, display, or sensor handlers.
- [x] 2.6 Implement bounded integer/fixed-point single-wheel proportional regulation using the approved `KP_Q8` formula, minimum starting power, deadband, power slew limit, trajectory acceleration, and reversal-through-zero behavior; do not add integral or derivative state.
- [x] 2.7 Implement atomic logical A/C motion, side-complete hold policy, zero-distance completion, finite-motion timeout, stall detection, explicit ID-qualified stop, and status reporting.
- [x] 2.8 Implement ownership arbitration: reject legacy A/C and input-1/3 commands while owned, allow remote STOP to win, and latch `EXTERNAL_OVERRIDE` for other manual overrides.
- [x] 2.9 Implement latched terminal-state recovery through safe re-arm and reset behavior; status polling must remain read-only.
- [ ] 2.10 Add generated-firmware tests for command dispatch, malformed/semantic rejection, session and motion identity, duplicate idempotency, sensor mode, controller timing/formula, paired output ordering/skew, stop, stall, stale sensor, timeout, reset safety, ownership, and compatibility with existing opcodes.
- [x] 2.11 Regenerate the tracked S-record and Java firmware image from the single assembled image and verify deterministic output, image-source consistency, image size, RAM/stack budgets, and non-overlapping RAM state.

## 3. Add the Java RCX regulated adapter

- [x] 3.1 Add an additive bounded fixed-frame request path to `RcxLink` for regulated commands, preserving existing send/request behavior, shared toggle state, channel serialization, and zero-allocation receive storage.
- [x] 3.2 Implement `RcxRegulatedProtocol` for payload encoding, exact reply lengths, session/motion identity, at-most-two retries, duplicate handling, and `LINK_UNKNOWN` resolution.
- [x] 3.3 Implement the RCX regulated motor/pair adapter using the existing `RegulatedMotor` semantics and preserve the open-loop `RcxMotor` API.
- [x] 3.4 Implement pair-owned relative/absolute rotation, pair-level speed/acceleration profile, Java-side tacho offsets, asynchronous acceptance, bounded completion waiting, movement state, stall reporting, and distinct error mapping for `STALLED`, `TIMED_OUT`, `FAULT`, `EXTERNAL_OVERRIDE`, `UNSUPPORTED`, and `LINK_UNKNOWN`.
- [ ] 3.5 Keep setup-time state allocation only; verify that motion polling and status reads allocate nothing on the board.
- [ ] 3.6 Add shared-link ownership checks so one owner serializes `RcxLink` use and direct `RcxBrick` operations cannot race regulated control.
- [ ] 3.7 Add Java unit tests for adapter semantics, single-side paired rotation, reset behavior, cached versus refreshed status, raw wire vectors, retry/idempotency for every command, stale status/IDs, quantized encoder values, asynchronous completion, stop, stall, timeout, and invalid configuration.

## 4. Integrate regulated local navigation

- [x] 4.1 Add a regulated differential-drive navigation type that composes the paired RCX controller with existing `DriveGeometry` and does not change power-based `DifferentialDrive` behavior.
- [x] 4.2 Convert millimetre travel and robot-heading rotations into bounded left/right encoder targets using independent encoder ratios and documented sign conventions.
- [ ] 4.3 Integrate final tacho positions with `DifferentialDriveOdometry`/`Pose` where the existing interfaces permit it, documenting degraded behavior when no valid heading source exists.
- [x] 4.4 Add navigation tests for symmetric travel, in-place turns, asymmetric gearing, invalid geometry, overflow/range handling, and target quantization.
- [ ] 4.5 Keep RCX modulo arithmetic inside the adapter and verify positive-heading/counter-clockwise sign conventions with effective track width calibration.

## 5. Prepare examples and validate end to end

- [ ] 5.1 Implement the `RcxRegulatedMotorCalibration` planning example under the existing RCX examples package, using the final public API names and documenting A/C, input-1/input-3, polarity, gearing, raw steps, quantization, wheel calibration, and effective track width.
- [ ] 5.2 Implement the `RcxRegulatedDifferentialDrive` planning example under the existing RCX examples package, using calibrated geometry and demonstrating finite travel, asymmetric in-place rotation, status handling, error states, and paired stop.
- [ ] 5.3 Verify both examples follow board-side zero-allocation guidance, start serial output before reporting, use documented wiring/constants, and preserve existing RCX example conventions.
- [ ] 5.4 Add a deterministic motor/encoder plant, raw-frame transport harness, dropped/duplicated/delayed/corrupt reply injector, reset/power-loss simulation, and assertions over both state and A/C outputs.
- [ ] 5.5 Run the normal Maven tests and the affected firmware/image/protocol test suites; retain generated assembly, shim, image, RAM/stack, and timing evidence for failures.
- [ ] 5.6 Validate both examples through the offline `juno-examples` Java → Juno → Arduino toolchain workflow and confirm existing RCX examples remain byte/protocol compatible.
- [ ] 5.7 If physical hardware validation is approved separately, deploy through the repository's supported workflow and record image checksum, cadence, profile, raw status frames, completion error, stop latency, stall, timeout, reset, legacy-command arbitration, and previous-image reflash results.
