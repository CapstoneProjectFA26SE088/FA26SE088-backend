---
name: filmlink-migration-database
description: >-
  Use when interacting with Flyway migrations, PostgreSQL 18 schemas, or JPA entity-to-schema alignment
  in FilmLink backend (ddl-auto: validate).
---

# FilmLink Database & Migration Runbook

Database evolution in FilmLink (`FA26SE088-backend`) is strictly controlled to ensure zero data drift across local, CI, and production environments.

---

## 1. Golden Rules of Database Management

> [!CRITICAL]
> 1. **Leader-Only Migration Creation**:
>    Only the leader writes migrations in this phase (SPEC-00 §6). Never create a table or column yourself unless explicitly instructed by the spec task or approved by the leader.
> 2. **Never Edit Merged Migrations**:
>    Never modify a migration file that has already been merged into `dev` or `main`. Flyway validates checksums on startup; modifying existing files breaks everyone's local build and CI.
> 3. **Strict Schema Validation (`ddl-auto: validate`)**:
>    Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`. Hibernate will **never** create or alter tables. Every JPA entity field must exactly match an existing column (name, data type, nullability).

---

## 2. PostgreSQL 18 Data Type Conventions

| Data Concept | PostgreSQL 18 Type | Java Entity Mapping | Notes |
|---|---|---|---|
| **Primary Key** | `bigint GENERATED ALWAYS AS IDENTITY` | `@Id @GeneratedValue(strategy = IDENTITY) private Long id;` | Inherited from `BaseEntity` (except append-only tables). |
| **Monetary Values** | `bigint` ending in `_vnd` | `private Long amountVnd;` | Stores amount in Vietnamese Dong. Never use float or double. |
| **Timestamps** | `timestamptz` (stored in UTC) | `private Instant createdAt;` | PostgreSQL version must run with UTC timezone. |
| **Enums** | `varchar` + `CHECK (col IN (...))` | `@Enumerated(EnumType.STRING)` | Enum constants must match `CHECK` values character-for-character. |
| **Optimistic Lock** | `integer DEFAULT 0` | `@Version private Integer version;` | Handled automatically by Hibernate. |
| **File Storage** | `varchar(255)` (`blob_key`) | `private String blobKey;` | Store only the storage blob key. **Never store raw URLs in the database.** |

---

## 3. Append-Only Tables

The following 11 tables have database triggers blocking `UPDATE` and `DELETE`. They do not have `updated_at` and **must not extend `BaseEntity`**:
1. `audit_logs`
2. `processing_stage_records`
3. `shipment_status_events`
4. `film_handovers`
5. `film_handover_photos`
6. `payment_gateway_events`
7. `commission_debt_reminders`
8. `consignment_handovers`
9. `consignment_handover_photos`
10. `moderation_actions`
11. `complaint_evidence_files`

---

## 4. Generating New Migrations (When Authorized)

When explicitly tasked with creating a migration:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/new-migration.ps1 -Domain <domain> -Description <description_snake_case>
```

- Target location: `src/main/resources/db/migration/V<yyyyMMdd>_<HHmm>__<domain>_<description>.sql`
- Verify against the test schema: `.\mvnw.cmd clean verify` runs Flyway on `DB_TEST_SCHEMA` (`filmlink_test`) before executing integration tests.
