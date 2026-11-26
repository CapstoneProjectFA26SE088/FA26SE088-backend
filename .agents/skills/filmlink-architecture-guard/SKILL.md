---
name: filmlink-architecture-guard
description: >-
  Deterministic architecture and anti-pattern enforcement skill for FilmLink backend.
  Use this skill whenever verifying code structure, refactoring, adding new classes,
  or ensuring compliance with FilmLink's 10 Golden Rules (R1-R10) and anti-slop guidelines.
---

# FilmLink Architecture & Anti-Pattern Guard

This skill automates deterministic structural enforcement for the FilmLink backend (`FA26SE088-backend`), replacing subjective guidelines with reproducible static checks as advocated by Lauren Tan ("All Code Is Slop (Except Mine)").

## Purpose

Agents frequently make subtle architecture mistakes when generating backend code:
- Injecting repositories directly into controllers (violates R1).
- Adding Lombok `@Data` to JPA entities, causing lazy initialization exceptions and infinite recursion (violates R2).
- Adding `try/catch` in controllers instead of delegating to `GlobalExceptionHandler` (violates R6).
- Injecting repositories across domain boundaries instead of calling services or publishing events (violates R4).
- Using prohibited timezone strings like `Asia/Saigon` or missing `zone = "Asia/Ho_Chi_Minh"` in `@Scheduled`.
- Calling `Instant.now()` directly instead of injecting `Clock`.
- Forcing append-only tables to extend `BaseEntity` (which breaks database trigger constraints).
- Leaving excuse comments (`// TODO: quick fix`, `// workaround`) to justify architectural debt.

---

## Quick Execution

Run the code smell and anti-pattern scanner from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-code-smells.ps1
```

Or run the full architecture check script:

```powershell
powershell -ExecutionPolicy Bypass -File .agents/skills/filmlink-architecture-guard/scripts/check-architecture.ps1
```

If any violations are detected, the script outputs the exact file, line, and rule violated, exiting with status code `1`.

---

## Architectural Rules Reference

| Rule | Constraint | Remediation |
|---|---|---|
| **R1** | Controller → Service (interface) only | Remove repository injection from controller; inject domain service instead. |
| **R2** | Entities never leave Service layer | Return DTOs or `PageResponse<T>`. Entity mapping stays inside Service/Mapper. |
| **R2 (Lombok)** | No `@Data` on `@Entity` | Replace `@Data` with `@Getter` and `@Setter`. |
| **R3** | `@Transactional` only in `ServiceImpl` | Remove `@Transactional` from controllers or repositories. Use `readOnly = true` for queries. |
| **R4** | Domain isolation for repositories | Replace cross-domain repository with that domain's Service (if lower tier) or publish a Spring `ApplicationEvent`. |
| **R5** | Validation in DTOs and Controller | Add Jakarta validation annotations (`@NotBlank`, `@NotNull`) to Request DTO and `@Valid` to controller parameter. |
| **R6** | No `try-catch` in Controller | Remove `try/catch`. Throw `BusinessException(ErrorCode.X)` in Service. |
| **R7** | URL format & Pagination | Ensure URL is `/api/v1/<plural-noun>` and pagination uses `PageResponse<T>` with `@PageableDefault`. |
| **R8** | Glossary & Javadoc | Check names against SRS. Add `/** BR-XX */` to service methods implementing business rules. |
| **R9** | External clients | Route external integration calls through `com.fa26se088.filmlink.client.*`. |
| **R10** | Pure static utils | Ensure classes in `utils/` are final with private constructors and contain only static pure methods. |

---

## Special Codebase Conventions

1. **Append-Only Tables Exemption**:
   The following 11 tables are append-only with database triggers blocking UPDATE/DELETE. They have no `updated_at` and **MUST NOT extend `BaseEntity`**:
   - `audit_logs`
   - `processing_stage_records`
   - `shipment_status_events`
   - `film_handovers`
   - `film_handover_photos`
   - `payment_gateway_events`
   - `commission_debt_reminders`
   - `consignment_handovers`
   - `consignment_handover_photos`
   - `moderation_actions`
   - `complaint_evidence_files`

2. **Time and Clock Injection**:
   - Never call `Instant.now()`, `LocalDate.now()`, or `LocalDateTime.now()` directly.
   - Always inject `java.time.Clock` (configured in `ClockConfig`) and call `Instant.now(clock)`.

3. **Handwritten Mappers**:
   - Mappers in `com.fa26se088.filmlink.mapper.*` are Spring `@Component` classes written by hand. Do NOT import or generate MapStruct annotations.

4. **Lazy Fetch on Relations**:
   - `@ManyToOne` and `@OneToOne` must explicitly specify `fetch = FetchType.LAZY`.
