# FilmLink Domain Tiers & Layer Dependency Matrix

## 1. Domain Tier Hierarchy

```text
  Tier 4 [Aggregation]      ai  ·  moderation  ·  dashboard
                                  │
  Tier 3 [Core Business]   order  ·  marketplace  ·  community
                                  │
  Tier 2 [Order Services] machine  ·  delivery  ·  archive  ·  payment
                                  │
  Tier 1 [Catalogue]              lab
                                  │
  Tier 0 [Foundation]     account  ·  masterdata  ·  notification  ·  audit
```

| Tier | Domains | Role & Constraints |
|:---:|---|---|
| **0** | `account`, `masterdata`, `notification`, `audit` | Foundation layer. Independent. Cannot inject services of higher tiers. |
| **1** | `lab` | Film lab catalogue & service packages. Can inject Tier 0 services. |
| **2** | `machine`, `delivery`, `archive`, `payment` | Supporting services for orders. Can inject Tier 0 and Tier 1 services. |
| **3** | `order`, `marketplace`, `community` | Core business transactions. Can inject Tier 0, 1, and 2 services. |
| **4** | `ai`, `moderation`, `dashboard` | Aggregation & AI assistance. Can inject lower tier services. `dashboard` is read-only with NO entities/repositories. |

---

## 2. Communication Rules

1. **Downward Calls (Synchronous)**:
   - A Service may directly inject and call Service interfaces of **LOWER** tiers.
   - Example: `ServiceOrderServiceImpl` (Tier 3) can inject `PaymentService` (Tier 2) and `FilmLabService` (Tier 1).

2. **Upward Information Flow (Asynchronous / Event-Driven)**:
   - A lower tier domain **NEVER** injects a service of a higher tier (would cause circular dependencies).
   - Instead, the lower tier publishes a Spring `ApplicationEvent` (Java record placed in `service/<publishing-domain>/event/`).
   - Listeners that must succeed together with the publisher use `@EventListener` + `@Transactional(propagation = MANDATORY)`.
   - Side effects that may fail independently (e.g. sending emails) run after commit.

---

## 3. Registered Cross-Domain Events

| Event Record | Publisher | Listener(s) | Trigger / Purpose |
|---|---|---|---|
| `LabRegistrationApprovedEvent` | `account` (Tier 0) | `lab` (Tier 1) | Admin approves lab application → create `FilmLab` record |
| `PaymentSucceededEvent` | `payment` (Tier 2) | `order` (Tier 3), `marketplace` (Tier 3) | VNPay IPN confirmed → transition order to `PAID` |
| `PromisedDateChangedEvent` | `machine` (Tier 2) | `order` (Tier 3) | Machine incident/delay → recalculate Promised Return Date |
| `NotificationEvent` | Any domain | `notification` (Tier 0) | Asynchronously dispatch push notification / email |

---

## 4. Client Adapter Matrix (`client/`)

Per Rule R9, all external integrations must be routed exclusively through interfaces in `com.fa26se088.filmlink.client.*`:

| Package | External Service | Client Interface | Primary Implementation |
|---|---|---|---|
| `client/payment` | VNPay Gateway | `PaymentClient` | VNPay Sandbox, checksum verification, IPN handling |
| `client/storage` | Azure Blob / Local | `StorageClient` | `upload(purpose, ...)` returns blobKey, `signedUrl(blobKey)` (15 min TTL) |
| `client/delivery` | Delivery Partner | `DeliveryClient` | Inter-city parcels & intra-city pickup tracking |
| `client/maps` | Google Maps | `GeocodingClient` | Lat/Long calculation & address geocoding |
| `client/notification` | Email / FCM | `EmailClient`, `FcmClient` | Local `log` provider or SMTP, Firebase push |
| `client/llm` | OpenAI API | `LlmClient` | Advisory chatbot (UC19) |
