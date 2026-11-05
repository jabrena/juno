# inheritance-generics-arrays-semantic-conformance Specification

## Purpose
TBD - created by archiving change inheritance-generics-arrays-conformance. Update Purpose after archive.
## Requirements
### Requirement: The three semantic areas are catalogued

The catalog SHALL define `inheritance`, `generics`, and `arrays` scopes and the areas
`inheritance.class-and-interface-dispatch`, `generics.classes-methods-and-erasure`, and
`arrays.creation-access-and-length`. They SHALL cite respectively JLS §§8.1.4, 8.1.5, 8.4.8.1, and 15.12.4.4;
§§4.4–4.6, 8.1.2, and 8.4.4; and §§10.1–10.7 and 15.10.1–15.10.3. Each SHALL reference a fixture and declare
Thumb-2, AVR, and Xtensa as `REQUIRED`.

#### Scenario: Catalog validation loads the requested categories

- **WHEN** the conformance catalog is validated
- **THEN** all three required areas, fixtures, JLS references, and backend claims are present

### Requirement: Inheritance dispatch is compared with OpenJDK

`InheritanceDispatch.java` SHALL exercise superclass-constructor invocation, inherited state, an overriding method
that invokes `super`, and dispatch through an interface reference.

#### Scenario: A derived instance is used through its base contracts

- **WHEN** the fixture runs on a required backend
- **THEN** its superclass, override, and interface-dispatch transcript equals OpenJDK

### Requirement: Generic types are compared with OpenJDK

`GenericTypes.java` SHALL use a generic class, generic methods, parameterized array values, and the safe casts javac
inserts after erasure, while avoiding boxing and collection-library dependencies.

#### Scenario: Erased generic values preserve their behavior

- **WHEN** the fixture runs on a required backend
- **THEN** its transcript equals OpenJDK

### Requirement: Array semantics are compared with OpenJDK

`ArraySemantics.java` SHALL exercise initializer and dimension creation, stores and loads, one- and two-dimensional
lengths, primitive narrowing stores, and low/high out-of-bounds reads caught as `ArrayIndexOutOfBoundsException`.

#### Scenario: Array bounds are observable

- **WHEN** either out-of-bounds read executes on a required backend
- **THEN** the fixture catches `ArrayIndexOutOfBoundsException` and its transcript equals OpenJDK

