## Context

Today the mapping from board to library is a hard-coded branch in `ArduinoCli.compile`:
- The UNO Q gets `--build-property` settings plus `--library <target/juno/libraries/JunoBearSSL>`, installed by
  `BearSslLibrary.installInto`.
- The UNO R3 WiFi Rev2 gets a `--build-property`.

`InstallDepsMojo` can already run `arduino-cli lib install <name>`. The compiler already knows which `ShimFeature`s a
program uses, because `RuntimeShim` gates includes and helpers on `uses(ShimFeature.X)`. The repo norm is that the
backend never branches on a specific board.

## Goals / Non-Goals

**Goals:**
- One mechanism through which a shim feature declares the native libraries it needs.
- The plugin links exactly that set of libraries.
- Repo-bundled libraries and pinned Library Manager libraries are both supported.
- Offline tests use the fake process executor.

**Non-Goals:**
- Changing which libraries existing programs link. BearSSL on the UNO Q stays as it is today, unconditional for
  that FQBN, unless a later change decides otherwise.
- Adding TensorFlow Lite Micro itself. That belongs to `esp32-cam-cat-detector`.
- The `.env` key allowlist idea.

## Decisions

- **Feature-driven, not FQBN-driven.** The library list is computed from the shim features the program uses. The
  board only decides whether a feature is allowed, through the existing capability gate in the `Linker`.
  - Alternative rejected: an `esp32:esp32:esp32cam` FQBN branch. The maintainer declined it in favour of a generic
    mechanism.
- **Two library kinds.**
  - *Bundled*: a repo module that provides an installer, like `BearSslLibrary`. It is copied into
    `target/juno/libraries` and passed with `--library`.
  - *Managed*: an Arduino Library Manager name plus an exact version, installed with `lib install name@version`.
- **Channel from compiler to plugin.** The compiled program exposes its required libraries to the Mojos. The concrete
  carrier (the report, or the compile-result API) is left to implementation, with one constraint: for programs
  that need no library, the existing reports must stay byte-identical.

## Risks / Trade-offs

- Library Manager versions can drift against the `esp32:esp32` core, which the repo does not pin → pin the library
  version, and record the core version used by CI.
- A bundled library could appear on the command line twice, for example BearSSL on the UNO Q → use de-duplicated,
  deterministic ordering.

## Open Questions

- Should the UNO Q's BearSSL move behind its shim feature (`HttpServer.beginTls`), or stay unconditional? The source
  does not decide this, so it is unresolved.
