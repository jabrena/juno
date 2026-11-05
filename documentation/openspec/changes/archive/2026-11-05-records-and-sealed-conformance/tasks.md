## 1. Catalog and fixtures

- [ ] 1.1 Add `records` and `sealed` scopes and the required areas to `catalog.xml`, with JLS classifications and `REQUIRED` status for Thumb-2, AVR, and Xtensa.
- [ ] 1.2 Add `RecordClasses.java` with components, declared behavior, normal and compact canonical constructors, and deterministic serial output.
- [ ] 1.3 Add `SealedTypes.java` with explicit sealed interface and class hierarchies, permitted final subclasses, and deterministic dispatch output.
- [ ] 1.4 Extend catalog validation tests so both new semantic areas are required and appear in coverage results.

## 2. Execution

- [ ] 2.1 Run the records and sealed fixtures under `QemuRunIT` and confirm their UNO R4 WiFi and UNO Q transcripts match OpenJDK.
- [ ] 2.2 Run the fixtures under `SimavrRunIT` and confirm their UNO WiFi Rev2 transcripts match OpenJDK.
- [ ] 2.3 Run the fixtures under `XtensaRunIT` and confirm their Nano ESP32 transcripts match OpenJDK.

## 3. Verification and closeout

- [ ] 3.1 Run `./mvnw -pl juno-compiler test -Dtest='Conformance*Test'` and `./mvnw test`.
- [ ] 3.2 Push the stacked branch and confirm its CI conformance backend jobs pass.
- [ ] 3.3 Tick the `records/` and `sealed/` items in issue #213 only after CI passes; leave the issue open for its remaining categories.
- [ ] 3.4 Archive this change only after the above work is complete and with explicit maintainer approval.
