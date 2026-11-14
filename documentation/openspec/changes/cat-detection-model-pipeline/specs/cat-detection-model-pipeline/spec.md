## ADDED Requirements

### Requirement: Dataset preparation runs from Maven without Python

The pipeline SHALL download the Oxford-IIIT Pet dataset, label cat images by their uppercase-initial file name,
convert every image to 64×64 8-bit grayscale, and produce separate training, validation and test sets. It SHALL do
this from a Maven invocation, with no Python process at any step.

#### Scenario: Dataset preparation completes without Python

- **WHEN** the maintainer runs the pipeline's dataset step on a machine without Python installed
- **THEN** it produces the cat / no_cat training, validation and test sets as 64×64 grayscale images

#### Scenario: Labels follow the file-name rule

- **WHEN** the downloaded archive contains `Bengal_1.jpg` and `beagle_1.jpg`
- **THEN** `Bengal_1.jpg` is labelled cat and `beagle_1.jpg` is labelled no_cat

### Requirement: The split and metrics are reproducible

The split SHALL be deterministic, derived from a fixed seed and recorded in tracked file lists. Re-running
evaluation on the same model and test set SHALL give the same metrics within a recorded tolerance. The exported
`.tflite` is not required to be byte-identical across retraining.

#### Scenario: Two runs give the same split

- **WHEN** the dataset step runs twice from the same seed and source archive
- **THEN** the train, validation and test file lists are identical

### Requirement: Training and export are Java-only and produce a TFLite Micro model

The trainer SHALL train the tiny CNN defined in the issue (three convolution blocks, global average pooling, dense
sigmoid output) on the build machine in Java. The exporter SHALL write a fully int8-quantized `.tflite` of at most
100 KB whose operators are all supported by TensorFlow Lite Micro in int8.

#### Scenario: The exported model fits the size and operator budget

- **WHEN** the exporter writes `cat_detector_int8.tflite`
- **THEN** the file is at most 100 KB, every tensor is int8, and every operator is in {Conv2D, ReLU, MaxPool,
  Mean/GlobalAveragePooling, FullyConnected, Logistic}

#### Scenario: The model loads in TensorFlow Lite Micro

- **WHEN** the exported model is loaded by a TensorFlow Lite Micro interpreter
- **THEN** tensor allocation succeeds and one inference on a 64×64 grayscale input returns a score

### Requirement: Evaluation is reported without a gate

The pipeline SHALL report held-out test accuracy and the false-positive rate on the non-cat negatives. v1 SHALL NOT
fail the build on a numeric accuracy threshold.

#### Scenario: An evaluation report is produced

- **WHEN** training finishes
- **THEN** a report lists held-out accuracy and the false-positive rate on each negative category, together with the
  seed and the tolerance

### Requirement: The model artifact is tracked and attributed

`cat_detector_int8.tflite` SHALL be committed in `juno-third-parties/juno-cat-detection`, beside a NOTICE that credits
Oxford-IIIT Pet and states that the model is licensed CC BY-SA 4.0, separately from the repository license.

#### Scenario: The NOTICE accompanies the model

- **WHEN** the model artifact is committed or regenerated
- **THEN** the NOTICE beside it names the dataset, its CC BY-SA 4.0 license, and the model's license

### Requirement: Training is isolated from the normal build and the board-side modules

Download, training and export SHALL run only on demand and never during `./mvnw verify`. Training dependencies
SHALL NOT be reachable from `juno-api`, `juno-compiler` or `juno-examples`, and an ArchUnit rule SHALL enforce this.

#### Scenario: verify does not train

- **WHEN** `./mvnw verify` runs on a clean checkout
- **THEN** no dataset is downloaded, no model is trained, and the tracked model is used as it is

#### Scenario: The architecture rule blocks leakage

- **WHEN** a class in `juno-api`, `juno-compiler` or `juno-examples` depends on a training library type
- **THEN** the architecture test fails
