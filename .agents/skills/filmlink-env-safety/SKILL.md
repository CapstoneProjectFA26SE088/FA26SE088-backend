---
name: filmlink-env-safety
description: >-
  Use whenever handling environment variables, configuration properties, or credentials (JWT, DB, VNPay,
  OpenAI, SMTP, Azure, Maps) in FilmLink backend. Enforces strict zero-touch protection of .env.
---

# FilmLink Environment & Secret Safety Runbook

The `.env` file at the repository root contains the developer's personal secrets and local database credentials. AI agents are strictly forbidden from touching, viewing, or editing this file.

---

## 1. Absolute Prohibitions

> [!CAUTION]
> 1. **DO NOT Touch `.env`**:
>    Never read, open, edit, delete, create, copy, or print the contents of `.env` or `.env.*` (the only exception is the template `.env.example`).
> 2. **DO NOT Print Secrets**:
>    Never echo, log, or include real secret values in assistant responses, unit tests, YAML files, or git commit messages.
> 3. **DO NOT Break CI**:
>    Any new environment variable `${VAR}` introduced in `application.yml` **MUST** provide a safe default (`${VAR:}`) or mock mode (e.g. `MAIL_PROVIDER=log`). Adding a bare required variable with no default will fail GitHub Actions CI.

---

## 2. Standard Protocol for Proposing Environment Changes

When a feature requires a new environment variable:

1. **Update `.env.example`**:
   Add the variable with a placeholder/dummy value and documentation comment:
   ```properties
   # VNPay Sandbox Terminal Code
   VNPAY_TMN_CODE=your_tmn_code_here
   ```
2. **Update `application.yml` with Fallback**:
   ```yaml
   filmlink:
     vnpay:
       tmn-code: ${VNPAY_TMN_CODE:}
   ```
3. **Instruct the Developer (Template)**:
   In your final response to the user, state clearly:
   > *"Vui lòng thêm biến sau vào file `.env` cá nhân của bạn:*
   > *`VNPAY_TMN_CODE=<mã_sandbox_của_bạn>` (dùng cho cổng thanh toán VNPay)*"

---

## 3. Handling Missing Variables During Local Testing

If a command or test fails due to a missing environment variable:
- Inform the user of the exact variable name that is missing and explain its purpose.
- Explain how to export it temporarily in the current shell session:
  ```powershell
  $env:DB_TEST_SCHEMA = "filmlink_test"
  ```
- **Never open or inspect `.env` to locate missing values.**

---

## 4. Automated Enforcement: `guard-env.ps1`

The verification pipeline executes `scripts/guard-env.ps1`:
- **Stage R0**: Calculates a cryptographic SHA256 snapshot of all `.env*` files in the repository.
- **Stage R5**: Recalculates and verifies hashes. If any hash differs or if a new `.env` file was created, verification **FAILS IMMEDIATELY**.
