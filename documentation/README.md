# Mutation testing baseline

Snapshot of the PIT (PiTest) mutation report for the whole build, kept as a reference for improving the tests.
Open [pit-report/index.html](pit-report/index.html) in a browser to browse it, or serve the folder with:

```bash
jwebserver -d "$(pwd)/documentation" -p 8000   # then open http://127.0.0.1:8000/pit-report/
```

Generated 2026-10-19 with:

```bash
./mvnw -B -fae -Ppitest -Dcoverage.level=0 verify
```

`coverage.level=0` only lifts the 80% gate for this measurement run, so every module reports its real score.
The committed gate remains 80%.

## Scores per module

| Module | Mutants | Killed | Mutation score | Test strength | Line coverage |
|---|---|---|---|---|---|
| juno-api | 2503 | 1680 | 67% | 73% | 86% |
| juno-compiler | 5597 | 3810 | 68% | 75% | 91% |
| juno-maven-plugin | 176 | 146 | 83% | 84% | 92% |
| juno-third-parties/juno-rcx-firmware | 835 | 696 | 83% | 85% | 97% |
| **All modules (aggregate)** | **9111** | **6332** | **69%** | **75%** | **91%** |

juno-site, juno-examples and the parent poms are not mutated.

## Reproducing

`juno-compiler` takes about two hours. Modules run with `-fae`, so one failing module does not stop the aggregate.
The aggregate is written to `coverage-aggregator/target/pit-reports`; this folder is a copy of it.
