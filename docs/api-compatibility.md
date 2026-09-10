# PaperReader API v1 — compatibility and rollout policy (Android consumer view)

Companion to `TECHNICAL_DEBT.md` (SHR-001, SHR-002, SHR-003) and the
`api-contract-compatibility` capability. It records what the Android client pins, what it promises,
and what it needs from the server side before shared contract debt can be closed.

## The pinned bundle

| | |
|---|---|
| Canonical bundle | `app/src/test/resources/contract/canonical/` — the provider's own files, vendored byte-exactly |
| Consumer bundle | `app/src/test/resources/contract/v1/` |
| Manifest | `contract/v1/manifest.json` — API version, contract version, provenance, SHA-256 per file |
| Current contract version | `paperreader-v1.0.0` |
| Verified by provider CI | **yes** — `CanonicalProviderContractTest` drives the same bundle through Spring |
| Drift gates | `ContractBundleIntegrityTest` (consumer bundle), `CanonicalContractTest` (provider digests) |
| Full gate | `./gradlew :app:verifyApiContract` |

There are two bundles, and the distinction matters.

**`contract/canonical/` is authoritative.** It is a byte-exact copy of `PaperReaderApi/contracts/v1`
— OpenAPI 3.1 for the wire shapes, `semantics.md` for the behavior JSON Schema cannot express, the
canonical fixtures, `VERSION`, and the provider's own `SHA256SUMS`. Never hand-edit it. To take a new
provider release, re-vendor the whole directory and let `CanonicalContractTest` re-verify the
digests.

**`contract/v1/` is this repository's consumer expectation**, and now derives from the canonical
bundle rather than competing with it. It exists for the edge cases the twelve canonical fixtures do
not enumerate — expired and malformed descriptors, mismatched response identity, duplicate IDs,
empty pages that still carry a cursor, unknown response fields. Its error bodies are the canonical
RFC 7807 Problem Details, so the two cannot disagree about error handling.

Editing a fixture without updating its checksum fails the build. Regenerate deliberately, as part
of a reviewed contract-version change:

```bash
python3 tools/update-contract-manifest.py app/src/test/resources/contract/v1
```

## What Android sends

| Situation | On the wire |
|---|---|
| No open-access restriction | `open_access` is **absent**, never `false` |
| Open-access restriction on | `open_access=true` |
| No PDF restriction | `pdf_available` absent |
| PDF restriction on | `pdf_available=true` |
| Work type selected | the canonical value only: `article`, `review`, `preprint`, `book_chapter`, `dataset` |
| Topic | normalized free text; never an OpenAlex topic ID |
| Publication year | one exact year in 1500–2100 |
| Pagination | opaque `cursor` only; no page numbers |

Absence and `false` mean the same thing to the contract, but an un-migrated server read
`open_access=false` as an active restriction (API-002). Omission is the only encoding that is safe
against both.

## What Android accepts

| Situation | Android behavior |
|---|---|
| Unknown optional response field | ignored; the domain model is unchanged |
| New access-state value | maps to the non-actionable `UNKNOWN` fallback |
| Documented legacy work type (`book-chapter`, `journal_article`, any letter case) | accepted on input; never emitted |
| Unrecognized work type | `OTHER`; the paper is kept |
| Blank or absent work type | `UNKNOWN` |
| Missing stable ID or title on an item | that item is rejected; a page of only such items is a contract failure |
| Negative `total_count` | treated as absent, never displayed |

## Status semantics Android relies on

These are business decision 3 in `TECHNICAL_DEBT.md`. Android's mapping is already in place; the
server rollout (API-004) is what remains.

| Response | Android outcome | Recoverable |
|---|---|---|
| `200`, empty `items`, no cursor | terminal "no matches" — not an error | n/a |
| `200`, empty `items`, new cursor | traversal continues, bounded; never "no matches" | n/a |
| `200`, `paper_id` matches, `descriptor: null` | "no PDF right now" — not malformed | no |
| `200` body that cannot be mapped | contract failure — **never** not-found | no |
| `200` about a different `paper_id` | contract failure | no |
| `400` `INVALID_REQUEST` | invalid request — never worded as an outage | no |
| `404` | not-found | no |
| `502` `OPENALEX_UNAVAILABLE` | service unavailable | yes |
| `503` `OUTBOUND_CAPACITY_EXHAUSTED` | service unavailable — same outcome as `502` | yes |
| `504` `OPENALEX_TIMEOUT` | timeout | yes |
| other `4xx`/`5xx` | generic HTTP outcome, status retained, body never surfaced | `>=500` |

No response body from an error status ever reaches presentation state.

## Change rules inside v1

Additive only, for as long as v1 is supported:

- **Allowed:** adding an optional response field; adding an enum value where the documented client
  fallback keeps behavior safe (access states fall back to non-actionable, work types to `OTHER`);
  adding a new endpoint.
- **Not allowed:** changing the meaning of an existing field, tightening or loosening a nullability
  rule, changing what a query parameter restricts, redefining an existing enum value, removing a
  field or endpoint during the support window, or moving a path.

Anything in the second list requires `/v2`. `/v1` remains available for the documented mobile
deprecation window, because installed Android clients cannot be upgraded on the server's schedule.

## Android support assumptions

Until release management sets a policy (SHR-003), the conservative rule applies:

- every released v1 client remains supported;
- no breaking v1 change may ship;
- rollback of a live integration happens through the build/server gate — never by downgrading the
  Room database, which is forward-migrated and holds user-owned saved and download data.

## Open gates

**Closed 2026-09-09.** ~~Where the canonical v1 bundle is published, and how Android CI
authenticates to fetch it.~~ The provider publishes it in its own repository at `contracts/v1`,
released as `paperreader-v1.0.0`. Android vendors it with its `SHA256SUMS`, so the copy carries its
own integrity proof and needs no CI authentication. Both repositories now drive the same fixtures
through their production stacks. **SHR-001 and SHR-002 are closed.**

Still open, and not closable from this repository:

1. **The supported v1 deprecation window and minimum-client policy**, owned by release management.
   **SHR-003 remains open.**
2. **How a new provider release notifies Android that a re-vendor is due.** The digest check fails
   loudly on a *mismatched* copy, but a stale copy of an older release is internally consistent and
   passes. Nothing yet watches for a version bump. Tracked under SHR-003.

A green Android contract suite on its own means only that the client is internally consistent with
the recorded decisions. What shows the two sides agree is both repositories verifying the same
`contractVersion` — which is now the case, and is what `CanonicalContractTest` and the provider's
`CanonicalProviderContractTest` exist to keep true.
