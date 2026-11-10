## 1. Cat detection model pipeline

- [ ] 1.1 Ask the maintainer to approve the Java training dependency (TensorFlow Java or DJL), per the AGENTS.md "Ask first" rule.
- [ ] 1.2 Run the conversion spike: train a throwaway CNN in Java, export it to int8 `.tflite`, and dump the operator list. Record pass or fail, and whether a Java-native exporter is needed.
- [ ] 1.3 If 1.2 fails, build the Java-native int8 exporter (post-training quantization plus the TFLite FlatBuffer writer), with unit tests on the operator list and tensor types.
- [ ] 1.4 Create `juno-third-parties/juno-cat-detection` (object-oriented, `@NullMarked`) and register it in `juno-third-parties/pom.xml`.
- [ ] 1.5 Implement the Oxford-IIIT Pet downloader (verifying the uppercase-initial labelling rule) and the 64×64 grayscale preprocessor.
- [ ] 1.6 Resolve the source and licenses of the empty-room and furniture negatives, then implement the deterministic split with a fixed seed and tracked file lists.
- [ ] 1.7 Implement the trainer for the issue's CNN and the evaluator (held-out accuracy, false-positive rate for each negative category, seed, tolerance).
- [ ] 1.8 Train, export, and commit `cat_detector_int8.tflite` (at most 100 KB, int8, TFLite Micro operators only) together with its NOTICE (CC BY-SA 4.0).
- [ ] 1.9 Add an ArchUnit rule that keeps training dependencies out of `juno-api`, `juno-compiler` and `juno-examples`, and confirm `./mvnw verify` never trains.
- [ ] 1.10 Document regeneration and attribution in `juno-site/src/main/resources/content/third-parties.md`.
