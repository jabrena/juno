---
name: juno-sonarqube
description: Use when analyzing, explaining, fixing, or verifying SonarQube findings in this repository, including running the local analysis, refreshing stale results, grouping issues by Maven module, applying targeted Java suppressions, or refactoring code to resolve a rule. Covers the repository's SonarQube/JaCoCo workflow and distinguishes intentional findings from defects.
---
# SonarQube in Juno

Use SonarQube to find actionable defects in the Java modules without weakening the analyzer or changing the
meaning of board programs merely to silence a rule. Treat every finding as a small code-review item: identify the
module, full path from the repository root, rule, severity, message, and whether the right response is a fix, a
verification, or a narrowly documented suppression.

## Start and refresh the analysis

From the repository root:

```bash
./scripts/sonar.sh analyze
```

The `analyze` action starts the local SonarQube service from `docker-compose.sonar.yml`, waits for its health
endpoint, initializes the local project, and runs:

```bash
./mvnw --batch-mode --no-transfer-progress -Pjacoco,sonar clean verify sonar:sonar
```

Credentials are read from `.sonar/sonar.env` by default, or from `SONAR_CREDENTIALS_FILE`. Never commit that file
or a token. The SonarQube project key is `juno` and the local dashboard is `http://localhost:9000/dashboard?id=juno`.

The analysis is asynchronous. After an upload, wait for the Compute Engine task to finish before reporting counts;
otherwise the dashboard or API can still show the previous revision. Re-run the query after processing and identify
the analyzed revision when comparing before and after results.

The `mcp` action starts the SonarQube MCP server against the same local instance:

```bash
./scripts/sonar.sh mcp
```

Use the configured SonarQube MCP connection for issue searches, rule explanations, module grouping, and quality-gate
checks when available. If it is unavailable, use the local dashboard or SonarQube Web API rather than inventing
issue counts.

## Report findings consistently

For each finding, report:

- severity: Blocker, Critical, Major, Minor, or Info;
- type/category: Bug, Vulnerability, Code Smell, or Security Hotspot;
- rule key, such as `java:S2189`;
- Maven module and the full repository-relative path, such as `juno-api/src/main/java/...`;
- line or method and the analyzer's message;
- disposition: fix, add a behavioral verification, refactor, or suppress with an explanation.

Group summaries by Maven module (`juno-api`, `juno-compiler`, `juno-maven-plugin`, `juno-examples`, and
`juno-third-parties/juno-rcx-firmware`) and then by rule. Do not describe a finding only by an IDE-shortened path.

## Choose the response

1. **Fix the defect** when the finding identifies incorrect behavior, unsafe input handling, a real nullness issue,
   an unbounded loop, a naming collision, or unnecessary complexity.
2. **Add verification** when the existing behavior is valid but the analyzer cannot prove an invariant. Make the
   invariant executable with a guard, precondition, assertion, or focused test, as appropriate for code that runs on
   the build machine versus the board.
3. **Refactor** when the code structure is the problem. Preserve public behavior, generated assembly, shim output,
   and report determinism; use the smallest object-oriented change that removes the rule violation.
4. **Suppress narrowly** only when the code is intentionally demonstrating or requiring the flagged construct and a
   code change would defeat that purpose. For Java rules, use the exact rule key, for example:

   ```java
   @SuppressWarnings("java:S2189")
   void run() {
       // Intentional perpetual board loop.
   }
   ```

   Prefer the narrowest scope that covers the intentional code (statement, method, or class), and add a nearby
   comment when the reason is not obvious. Never use a broad `all` suppression or suppress a real defect just to
   reduce the dashboard count.

## Repository-specific validation

Run the smallest relevant check after each logical fix, then run the full suite before handing off:

```bash
./mvnw -pl juno-api test                 # replace with the affected module
./mvnw test
```

When changing `juno-api`, `juno-compiler`, `linker`, or `backend`, add or extend the appropriate compiler,
generated-assembly, or toolchain test and verify an affected example with `juno:verify`. Preserve byte-identical
generated `.S`, `Shim.cpp`, and reports for refactorings. Follow `juno-debugging` for compiler/generated-output or
hardware behavior, and `juno-zero-allocation` for board-memory or allocation findings.

After code changes, refresh Sonar with `./scripts/sonar.sh analyze`, wait for processing, and verify that the target
issue is resolved without introducing new findings or lowering the relevant quality gate. Keep `target/`, `docs/`,
and other generated output out of manual edits.
