## 1. Catalog and fixtures

- [ ] 1.1 Add `inheritance`, `generics`, and `arrays` scopes and required areas to the catalog.
- [ ] 1.2 Add the focused inheritance-dispatch fixture.
- [ ] 1.3 Add the focused generic-erasure fixture.
- [ ] 1.4 Add the focused array-semantics fixture, including caught bounds failures.
- [ ] 1.5 Require all three areas in catalog validation and coverage reporting.

## 2. Execution

- [ ] 2.1 Run all three fixtures under Thumb-2/QEMU and compare with OpenJDK.
- [ ] 2.2 Run all three fixtures under AVR/simavr and compare with OpenJDK.
- [ ] 2.3 Run all three fixtures under Xtensa/Espressif-QEMU and compare with OpenJDK.

## 3. Verification and closeout

- [ ] 3.1 Run strict OpenSpec validation and the focused conformance tests, followed by `./mvnw test`.
- [ ] 3.2 Push the stacked branch and confirm CI passes.
- [ ] 3.3 Update the corresponding issue categories only after CI passes.
