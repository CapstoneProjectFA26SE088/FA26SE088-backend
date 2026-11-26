---
name: filmlink-feature-builder
description: >-
  Use when implementing or modifying any feature, endpoint, or service in FilmLink backend (Spring Boot 4.1.1, Java 21).
  Enforces the deterministic 11-step feature workflow from spec analysis to verification and git commit.
---

# FilmLink Feature Builder Runbook

Follow this strict 11-step workflow whenever implementing a task in FilmLink backend.

---

## 11-Step Implementation Procedure

### Step 1: Spec & Requirement Analysis
- Read `docs/spec/FA26SE088_SPEC-00_Common_v0.1.md` and your assigned spec file.
- Identify the primary domain, affected lower-tier domains, use cases (UC), business rules (BR), and test case IDs (`TC-xxx`).
- Create task branch: `git switch dev && git pull && git checkout -b feature/FA26-<ticket>-<slug>`.

### Step 2: Database Migration Check
- Read the migration files of every table touched (`src/main/resources/db/migration/`).
- **Never create a table or column yourself.** Only the leader writes migrations in this phase (SPEC-00 §6).
- If a column or table is missing, stop and consult the leader.

### Step 3: JPA Entity
- Place in `src/main/java/com/fa26se088/filmlink/entity/<domain>/`.
- Standard entities extend `BaseEntity` (inheriting `Long id`, `Instant createdAt`, `Instant updatedAt`).
- **Append-only entities** (`audit_logs`, `processing_stage_records`, `shipment_status_events`, `film_handovers`, etc.) **MUST NOT extend `BaseEntity`**.
- Use `@Getter` and `@Setter`. **Never use `@Data` or `@EqualsAndHashCode`**.
- Explicitly set `fetch = FetchType.LAZY` on `@ManyToOne` and `@OneToOne`.
- Enum fields must use `@Enumerated(EnumType.STRING)` matching the PostgreSQL `CHECK` constraint.

### Step 4: Spring Data Repository
- Place in `src/main/java/com/fa26se088/filmlink/repository/<domain>/`.
- Interface extends `JpaRepository<Entity, Long>`.
- Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` for rows subject to concurrent updates.
- Repositories of domain X must **ONLY** be injected into `ServiceImpl` of domain X.

### Step 5: DTOs
- Place in `src/main/java/com/fa26se088/filmlink/dto/<domain>/request/` and `.../response/`.
- All DTOs must be Java **records**.
- Request DTOs must use Jakarta validation (`@NotNull`, `@NotBlank`, `@Size`, etc.).
- Response DTOs never expose entity objects, secrets, or internal password hashes.

### Step 6: Mapper
- Place in `src/main/java/com/fa26se088/filmlink/mapper/<domain>/`.
- Implement as a handwritten Spring `@Component` (e.g. `LabApplicationMapper`). Do not use MapStruct.

### Step 7: Service Interface & Implementation
- Interface in `service/<domain>/`, implementation in `service/<domain>/impl/`.
- Annotate `ServiceImpl` with `@Service` and `@Transactional` (use `readOnly = true` for query methods).
- Use constructor injection via Lombok `@RequiredArgsConstructor`.
- Business rules throw `BusinessException(ErrorCode.<CODE>, "message")`.
- Document business rule ID in Javadoc: `/** BR-17: the order reaches the lab only after payment. */`.
- Inject `java.time.Clock` instead of calling `Instant.now()` directly.
- Cross-domain calls: inject lower-tier service interfaces directly, or publish `ApplicationEvent` for higher tiers.

### Step 8: REST Controller
- Place in `src/main/java/com/fa26se088/filmlink/controller/<domain>/`.
- Base URL format: `@RequestMapping("/api/v1/<plural-noun>")`.
- Method parameters: `@Valid @RequestBody`, `@AuthenticationPrincipal AuthenticatedAccount account`.
- List endpoints: return `PageResponse<T>`, accept `Pageable` with `@PageableDefault`.
- **Zero try-catch blocks**: all exceptions are handled centrally by `GlobalExceptionHandler`.

### Step 9: Testing
- Unit tests: Mockito + JUnit 5 in `src/test/java/.../service/<domain>/`.
- Integration tests: `@SpringBootTest` + MockMvc against `DB_TEST_SCHEMA` (schema `filmlink_test`).
- **Test ID Convention**: Every test method's `@DisplayName` must start with its test case ID:
  ```java
  @Test
  @DisplayName("TC-F08-01 create order with 2 rolls returns AWAITING_PAYMENT and 220,000 VND")
  void createOrder_success() { ... }
  ```

### Step 10: Verification
- Run `powershell -ExecutionPolicy Bypass -File scripts/verify.ps1`.
- Ensure all stages pass (exit code 0) and inspect `target/verification/summary.txt`.
- Create a sample `requests/<feature>.http` file demonstrating happy path and error responses.

### Step 11: Git Commit
- Follow Conventional Commits: `<type>(<scope>): <description> FA26-<number>`.
- Example: `feat(order): create service order FA26-80`.
- The agent **never pushes or merges**; the developer reviews diffs and pushes.
