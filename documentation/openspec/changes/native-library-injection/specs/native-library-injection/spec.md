## ADDED Requirements

### Requirement: Required libraries follow used shim features

The compiler SHALL derive a program's required native libraries from the shim features the program uses. The plugin
SHALL pass each required library to `arduino-cli compile` without any branch on a specific board FQBN.

#### Scenario: A program using a library-backed feature links that library

- **WHEN** a program uses a shim feature that declares a native library, and is compiled with `juno:verify` for a
  board whose capabilities allow that feature
- **THEN** the `arduino-cli compile` invocation includes `--library` for that library

#### Scenario: A program that uses no library-backed feature is unaffected

- **WHEN** a program that uses no library-backed shim feature is compiled
- **THEN** the `arduino-cli compile` arguments are identical to the arguments produced before this change

### Requirement: Bundled and managed libraries are supported

A library declaration SHALL be either:
- a repo-bundled library, installed beside the generated sketches and passed by absolute path, or
- an Arduino Library Manager library with an exact pinned version, installed by `InstallDepsMojo`.

#### Scenario: A managed library is installed at its pinned version

- **WHEN** `InstallDepsMojo` runs for a project whose programs declare a managed library
- **THEN** it installs that library at the declared version, not the latest version

#### Scenario: A bundled library is installed once per version

- **WHEN** two programs that need the same bundled library are compiled in one build
- **THEN** the library is installed into `target/juno/libraries` once, and both compilations reference the same path

### Requirement: Existing output and behaviour are preserved

The change SHALL keep every existing example's generated `.S`, `Shim.cpp` and report byte-identical. It SHALL keep
the existing UNO Q and UNO R3 WiFi Rev2 `--build-property` and BearSSL behaviour.

#### Scenario: UNO Q BearSSL is still linked

- **WHEN** a program is compiled for `arduino:zephyr:unoq`
- **THEN** the invocation still includes the bundled BearSSL `--library` and the existing build properties

#### Scenario: Generated artifacts are unchanged

- **WHEN** the existing examples are regenerated after this change
- **THEN** their `.S`, `Shim.cpp` and report files are byte-identical to the files from before the change

### Requirement: Library injection is testable offline

The plugin tests SHALL verify the library arguments with the fake process executor and SHALL NOT touch hardware.

#### Scenario: Fake executor captures library arguments

- **WHEN** a Mojo test compiles a program that declares a bundled library and a managed library
- **THEN** the captured `arduino-cli` commands show the install of the pinned managed library and the `--library`
  argument for the bundled library
