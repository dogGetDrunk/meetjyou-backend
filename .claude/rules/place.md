---
description: Travel destination rules (region, place, SBIZ import, search) — loaded only when working in place/
paths:
  - "**/place/**"
  - "**/db/migration/V4[4-6]__*"
---

# Place Module

Travel destination = region (required, '00' 미지정 when none) + place (optional), on post / party / plan.

## Region
- `region` changes only through Flyway (seed `V45`, built from the SBIZ file by `.pipeline/tasks/20261109-place-search/scripts/seed.py`); `RegionCatalog` loads it once per process and every response reads it from memory — no region joins
- 일반구 hang under their city (`41111` → `41110` → `41`); a city/시·도 filter covers all descendants (`selfAndDescendants`)
- '00' is `searchable=false`: never a search result, rejected as an explicit `regionCode` (400)
- H2 tests get the same rows through `hibernate.hbm2ddl.import_files` = `V45` (application-test.yml) — a new region migration must stay loadable by that multi-line extractor or get its own entry

## Destinations on post / party / plan
- Transition period (decided 2026-11-09): `location`/`destination` strings stay required; `regionCode`/`placeId` are optional extras. Dropping the strings is a separate task after the app ships
- `TravelDestinationResolver`: re-sending the stored pair is accepted without the hierarchy check — an import may move a place to another district, and the post keeps the region the author chose (never bulk-synced)
- Party gets its destination only from the post (`createParty`, `syncFromPost` under the party row lock)
- `GetPlanResponse.region/place` are nullable with defaults: `party.planSnapshot` JSON written before them must still deserialize

## SBIZ import (`place/importer`)
- One run per process (`AtomicBoolean`); the executor is private to `PlaceImportService` — registering any `Executor` bean switches off Boot's `applicationTaskExecutor`
- Apply order in `PlaceImportWriter.apply`: same-key update → 1:1 rematch → insert → deactivate. Deactivation runs only after the rest succeeded, so a failed run never hides open stores. Each step commits per `chunkSize` rows (short X locks; post writes FK-check `place`)
- Rematch candidates are the leftovers on both sides after key matching (inactive places included), compared on full `(region_code, search_name, road_address)`; anything but exactly one-to-one is insert + deactivate
- Completeness gate before applying: every 시·도 with active places must stage ≥ 50% of them; empty staging fails. Never drop it to "save" a partial file
- `place` rows are never deleted (posts point at them); `place_import_staging` is emptied with TRUNCATE at the start of a run (needs DROP privilege)
- Portal endpoints are data.go.kr internals: a portal change fails the run (Discord alert, `place` untouched)

## Search (`PlaceSearchService`, `PlaceSearchRepository`)
- No `ORDER BY` in place queries: a sort reads every match first ('카페' = 2.5만 rows > the 128MB buffer pool → 9 s in prod). Order past exact names is not a contract
- Paths: exact name (region index) → then full-text (no region / region with > `REGION_SCAN_MAX_PLACES` active places) or region-index `LIKE` scan (small region). Both paths must return the same places: `PlaceSearchQuery` only requires tokens the ngram parser surely indexed (no stopword tokens, no one-char words, punctuation splits words), so full-text candidates ⊇ `LIKE` matches
- Region-index queries keep `NO_MRR` (several region codes → MRR collects every match, no early stop) and `FORCE INDEX` (> 200 IN values → statistics estimate → full scan seen)
- `PlaceRegionStats` (per-region active counts) only picks the path; `PlaceImportService` invalidates it after a successful import
- Cost guard: `PlaceSearchCostDockerTest` (rows read per search must not grow with matches); measurements in `.pipeline/tasks/20261109-place-search-perf/spec.md`

**Testing:** writer SQL and search (ngram FULLTEXT) are MySQL-only → `PlaceImportDockerTest`, `PlaceSearchDockerTest`, `PlaceSearchPathDockerTest`, `PlaceSearchCostDockerTest`, `RegionSchemaDockerTest`. Services built by hand use `PlaceTestDoubles` (real catalog over a 미지정-only list, not mockk — `clearAllMocks()` would wipe stubs).
