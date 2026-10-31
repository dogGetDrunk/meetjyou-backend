---
description: Terms module rules — loaded only when working in terms/ package
paths:
  - "**/terms/**"
---

# Terms Module

- Required-terms consent is enforced only at registration (`UserService.createUser` → `validateRequiredTermsAgreement`)
- `TermsReconsentEvent` only sends a push (notification outbox) — it does not block API access for users who haven't re-consented
- Active terms = `status = ACTIVE` **and** `effectiveAt <= now`
- `user_terms` is an append-only consent history: each change inserts a row, the latest `id` per (user, terms type) is the current state. Never update rows in place or add a unique key over `action` — the same action must be insertable again (V41, #224). The H2 schema lacks Flyway constraints, so pin schema behavior in `src/dockerTest` (`UserTermsConsentHistoryDockerTest`)
- **Open issue:** publishing sets the previous version INACTIVE immediately; if the new version's `effectiveAt` is in the future, that terms type disappears until then and required-consent validation silently skips it
