## ADDED Requirements

### Requirement: Bundled sketch sources follow used shim features

A shim feature MAY declare bundled C++ source. The compiler SHALL emit that source into the generated sketch only when
the program uses the feature. The emission SHALL NOT branch on a specific board FQBN.

#### Scenario: A program using the feature gets the bundled source

- **WHEN** a program uses a shim feature that declares bundled source, and is compiled for a board whose capabilities
  allow that feature
- **THEN** the generated sketch contains that source

#### Scenario: A program that does not use the feature is unaffected

- **WHEN** a program that does not use the feature is compiled
- **THEN** the generated sketch, the `.S`, the `Shim.cpp`, the report and the `arduino-cli` arguments are identical to
  those produced before this change

### Requirement: Managed libraries are pinned

`InstallDepsMojo` SHALL install each managed Arduino library at its declared exact version, never at the latest version.

#### Scenario: The pinned library is installed at its version

- **WHEN** `InstallDepsMojo` runs
- **THEN** the TensorFlow Lite Micro library is installed at the version declared in the Mojo, and the command line shows
  that version

### Requirement: Existing output and behaviour are preserved

The change SHALL keep every existing example's generated `.S`, `Shim.cpp` and report byte-identical. It SHALL NOT add a
`--library` argument to any `arduino-cli compile` invocation. The existing `--build-property` behaviour for the Portenta
H7 and the Pico 2 W secure firmware SHALL be unchanged.

#### Scenario: Generated artifacts are unchanged

- **WHEN** the existing examples are regenerated after this change
- **THEN** their `.S`, `Shim.cpp` and report files are byte-identical to the files from before the change

#### Scenario: No library argument is added

- **WHEN** any existing example is compiled
- **THEN** the `arduino-cli compile` arguments contain no `--library` argument

### Requirement: Library handling is testable offline

The plugin tests SHALL verify the install commands and the sketch contents with the fake process executor, and SHALL NOT
touch hardware.

#### Scenario: Fake executor captures the pinned install

- **WHEN** a Mojo test runs `InstallDepsMojo` with a pinned managed library
- **THEN** the captured `arduino-cli` commands show `lib install <name>@<version>`
