---
description: Terms module rules — loaded only when working in terms/ package
paths:
  - "**/terms/**"
---

# Terms Module

- Required-terms consent is enforced only at registration (`UserAuthService` → `validateRequiredTermsAgreement`)
- `TermsReconsentEvent` only sends a push (notification outbox) — it does not block API access for users who haven't re-consented
- Active terms = `status = ACTIVE` **and** `effectiveAt <= now`
- **Open issue:** publishing sets the previous version INACTIVE immediately; if the new version's `effectiveAt` is in the future, that terms type disappears until then and required-consent validation silently skips it
