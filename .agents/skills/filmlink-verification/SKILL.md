---
name: filmlink-verification
description: >-
  Use after writing or modifying any code in FilmLink backend and before declaring any task complete.
  Executes the multi-stage verification pipeline, checks test ID conventions (TC-xxx), and produces
  the verification artifact target/verification/summary.txt.
---

# FilmLink Verification Runbook

In accordance with Lauren Tan's thesis ("All Code Is Slop (Except Mine)"), verification consumes the majority of agent tokens (~70%). An agent never declares a task complete simply because code was written; it must be verified deterministically.

---

## 1. Quick Execution

From the repository root on Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify.ps1
```

Or individual target verification during development:
- Checkstyle & Compile: `.\mvnw.cmd -B -q compile checkstyle:check`
- Smell Check: `powershell -File scripts/check-code-smells.ps1`
- Single Unit Test: `.\mvnw.cmd test -Dtest=FilmLabServiceImplTest`
- Single Integration Test: `.\mvnw.cmd verify -Dtest=FilmLabControllerIntegrationTest`

---

## 2. Definition of Done (DoD) per AGENTS.md

A task is **DONE** only when:
1. All `Must` tests specified in your spec file exist and pass.
2. Every test method's `@DisplayName` starts with its test case ID (e.g., `@DisplayName("TC-F08-01 create order returns AWAITING_PAYMENT")`).
3. `.\mvnw.cmd clean verify` passes locally (exit code 0).
4. `scripts/guard-env.ps1 -Mode check` verifies that `.env` was never modified.
5. The verification summary exists at `target/verification/summary.txt`.

---

## 3. Failure Remediation Guide

If any stage of `scripts/verify.ps1` fails:

| Stage | Root Cause | Remediation |
|---|---|---|
| **R0/R5: guard-env** | Agent touched `.env` | **STOP IMMEDIATELY**. Do not try to restore secrets. Report to the user. |
| **R1: compile / checkstyle** | Syntax or Google Java style violation | Fix indentation (2 spaces), remove wildcard imports (`import *`), fix type mismatch. |
| **R2: code smells** | Anti-pattern detected (S01–S10) | Inspect smell output. Remove `@Data`, remove controller `try/catch`, remove workaround comments. |
| **R3: unit tests** | Assertion or mock failure | Debug logic in `ServiceImpl` or client. Never remove assertions or `@Disabled` tests. |
| **R4: integration tests** | Hibernate validation or DB failure | Ensure entity matches Flyway migration (`ddl-auto: validate`). Check `DB_TEST_SCHEMA`. |

> [!CAUTION]
> **Retry Limit Rule**: Maximum 3 fix-and-verify attempts for the same error. If still failing after 3 attempts, **STOP** and report to the user:
> - Failing stage
> - Relevant stack trace
> - Working hypothesis and proposed solution
> Never disable tests or bypass checks to make the gate pass!
