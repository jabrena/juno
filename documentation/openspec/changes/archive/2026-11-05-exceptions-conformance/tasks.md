## 1. Catalog and fixtures

- [ ] 1.1 Add the `exceptions` scope with its JLS sections, each covered or skipped with a reason.
- [ ] 1.2 Add `ExceptionCauses`, `ThrowAndCatch`, `FinallyAndUnwinding`, and `TryWithResources` under `programs/exceptions/`.
- [ ] 1.3 Add the four required exception areas that reference them, and register them in the required-area list.
- [ ] 1.4 Extend the coverage test with the `exceptions` scope.

## 2. Verification

- [ ] 2.1 Run strict OpenSpec validation and focused conformance tests (`ConformanceCatalogTest`).
- [ ] 2.2 Run the four fixtures under Thumb-2/QEMU, AVR/simavr, and Xtensa/Espressif-QEMU, comparing with OpenJDK.
- [ ] 2.3 Push the stacked branch, open its PR against `feat/strings-conformance`, and confirm CI before updating the
  issue category.
