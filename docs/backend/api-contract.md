# PaperReader backend API contract

This is the endpoint and access-state contract the Android client already codes against via
`PaperReaderApi` (`app/src/main/java/com/example/paperReader/data/remote/PaperReaderApi.kt`) and the
DTOs in `data/remote/dto/PaperDtos.kt`. The PaperReader server is still being built (see
`docs/backend/demo-mode.md`); this document is what a real implementation must satisfy so that
building the `integration` or `production` flavor is a configuration change, not a client rewrite.

The **normative, checksum-pinned** statement of what this client expects lives in
`app/src/test/resources/contract/v1/`, and the rules for changing it live in
`docs/api-compatibility.md`. This document is the prose companion to that bundle; where they
disagree, the bundle and its tests win. `TECHNICAL_DEBT.md` records the server-side gaps that are
known and accepted.

## Server-side responsibilities

The backend, not the Android client, owns:

- Holding the OpenAlex API key and making OpenAlex requests. It is never sent to, stored in, or
  logged by the client (see `specs/backend-ready-data-layer/spec.md`, "Secret-free Android client").
- Inspecting **every** candidate location OpenAlex reports for a work (not just OpenAlex's
  `best_oa_location` field) to determine whether a genuine, freely-reachable PDF or open-HTML
  source exists.
- Scraping only content that is actually freely reachable, and only on the backend — the client
  never scrapes, never infers access from a publisher URL, and never treats a landing-page link as
  proof of availability.
- Optionally generating a PDF from freely-reachable open content it scraped, and reporting that
  through the same confirmed-PDF contract as an original publisher PDF, with `generated: true` and
  provenance text so the client can attribute it correctly.

## Endpoints

All paths are relative to the build's configured base URL — `paperReaderBackendBaseUrl` for the
`integration` flavor, the pinned `https://barretolab.tech/` constant for `production`. Because the
paths are relative, that base URL is the bare origin, not a prefix ending in `/v1/papers/`.

### `GET /v1/papers/search`

Query parameters (all optional except `query`):

| Param | Type | Notes |
|---|---|---|
| `query` | string | Non-blank search text. |
| `cursor` | string | Opaque cursor from a previous response's `next_cursor`; omit for the first page. Never a page number — see design.md decision 6. |
| `sort` | string | One of `relevance` (default), `newest`, `citations`. |
| `open_access` | boolean | Restrict to papers with any open-access indication. **Only `true` is a restriction.** The client omits the parameter entirely when the filter is off — it never sends `false` (see `TECHNICAL_DEBT.md` API-002). |
| `pdf_available` | boolean | Restrict to papers with a confirmed `DIRECT_PDF` state. Same omission rule. |
| `publication_year` | int | Exact year filter. |
| `work_type` | string | One of `article`, `review`, `preprint`, `book_chapter`, `dataset`. The client **emits only these canonical values**; on input it also accepts the documented legacy aliases `book-chapter` and `journal_article` in any letter case. Blank/omitted maps to `UNKNOWN`, any other unrecognized value to `OTHER` — never an error, and never a lost paper. |
| `topic` | string | Free-text topic filter, never an OpenAlex topic ID (recorded business decision 2). |
| `publication_year` | int | Must be within 1500–2100; the client will not request a year it would refuse to display. |

Response body — `PaperSearchResponseDto`:

```jsonc
{
  "items": [ /* PaperDto, see below */ ],
  "next_cursor": "opaque-string-or-null",
  "total_count": 123 // optional; omit if unknown rather than sending a guess. The client never
                     // derives a total from the rows it loaded, so an omitted total simply means
                     // no total is shown. A negative value is treated as absent.
}
```

### `GET /v1/papers/{paperId}`

`paperId` is the stable OpenAlex work ID (`^W[0-9]+$`; the client rejects anything else — see
`specs/backend-ready-data-layer/spec.md`, "Required identity is missing"). Response —
`PaperDetailResponseDto`: `{ "paper": PaperDto }`.

The client rejects a detail response whose paper is not the one it asked about, and treats
`{"paper": null}` as malformed data — **not** as "not found". A missing paper is HTTP 404.

### `GET /v1/papers/{paperId}/download`

The transfer-time freshness authority (recorded business decision 5). Android calls this
immediately before **every** initial and retried download and enqueues only from what it returns.
Response — `DownloadDescriptorResponseDto`:
`{ "paper_id": "...", "descriptor": ConfirmedPdfDownloadDescriptorDto? }`.

| Response | Client behavior |
|---|---|
| `200`, matching `paper_id`, valid unexpired HTTPS descriptor | the transfer is enqueued from that descriptor; a retry replaces the stored URL and MIME type first |
| `200`, matching `paper_id`, `descriptor: null` | "no PDF available right now" — a normal answer, not malformed, no work enqueued |
| `200`, different `paper_id` | contract failure, no work enqueued |
| `200`, descriptor without a valid HTTPS URL, or already expired | fails closed, no work enqueued |
| `404` | not-found, no work enqueued |
| `502` / `504` / offline | recoverable failure; the existing download record and any completed file are left untouched |

In no case does the client fall back to a URL it had cached.

### There is no client `access:refresh`

An earlier draft of this contract included `POST /v1/papers/{paperId}/access:refresh`. The Android
client **does not call it** and no longer implements it: it had no production call site
(`TECHNICAL_DEBT.md` AND-011), and full detail refresh plus the download confirmation above already
cover every freshness need. The server may keep the endpoint; removing or repurposing it is a
separate versioned change, and reintroducing a client call needs a defined product flow.

## Shared shapes

`PaperDto` (all fields except `id`/`title` are optional and unknown extra fields are ignored —
`ignoreUnknownKeys = true` — so the backend can add fields without breaking the client):

```jsonc
{
  "id": "W2741809807",           // required, must match ^W[0-9]+$ or the item is dropped
  "title": "...",                 // required, non-blank or the item is dropped
  "authors": [ { "id": "...", "name": "...", "orcid": "..." } ],
  "venue": "...",
  "year": 2024,
  "type": "article",              // article | review | preprint | book_chapter | dataset; blank/omitted -> UNKNOWN, anything else -> OTHER
  "topics": [ { "id": "...", "name": "...", "score": 0.9 } ],
  "citation_count": 151,
  "abstract": "...",
  "access": AccessResolutionDto   // see below; omit or send null for "not yet checked"
}
```

## The access-state contract

`AccessResolutionDto`:

```jsonc
{
  "state": "DIRECT_PDF",          // see table below
  "source_url": "https://...",    // OPEN_FULL_TEXT only; must be a valid https:// URL with a host and no embedded userinfo
  "descriptor": ConfirmedPdfDownloadDescriptorDto, // DIRECT_PDF only
  "provenance": "Institutional repository" // optional, either state
}
```

`ConfirmedPdfDownloadDescriptorDto`:

```jsonc
{
  "url": "https://...",           // required, valid https:// URL
  "mime_type": "application/pdf", // optional
  "provenance": "...",            // optional
  "generated": false,             // true if the backend scraped+generated this PDF rather than serving an original
  "expires_at_epoch_millis": null // optional; if present must be > 0
}
```

| `state` | Meaning | Client requirement to become actionable |
|---|---|---|
| `DIRECT_PDF` | A confirmed, freely downloadable PDF exists (original or backend-generated). | `descriptor.url` must be a valid `https://` URL. Any other malformed field in the descriptor fails the whole state closed. |
| `OPEN_FULL_TEXT` | A confirmed, freely readable HTML/full-text source exists, but not as a direct PDF. | `source_url` must be a valid `https://` URL. |
| `NO_FREE_FULL_TEXT` | Backend checked and found nothing freely accessible. | Never actionable. |
| `PAYWALLED` | Backend found the work but access requires payment/subscription. | Never actionable. |
| `UNKNOWN` | Not yet checked, or the backend explicitly can't determine access. | Never actionable. Also the client's fallback for any state value it doesn't recognize (forward compatibility — see `specs/backend-ready-data-layer/spec.md`, "Unknown future access value"). |
| `CHECKING` | A check is in progress (e.g. async verification). | Never actionable. |
| `ERROR` | The backend's own access check failed. | Never actionable. |

**Fail-closed rule:** the client (`PaperMappers.mapAccessResolution`) only ever produces an
actionable `DirectPdf`/`OpenFullText` domain value when the state *and* its accompanying URL are
both present and valid. A `DIRECT_PDF` state with a missing or `http://` (non-HTTPS) descriptor
URL, or an `OPEN_FULL_TEXT` state with a malformed `source_url`, is downgraded to `Unknown` rather
than rendering a broken or misleading action — see `specs/verified-paper-access/spec.md`. This is
enforced identically for a live response and for a value re-read from the local Room cache
(`LocalMappers.toDomain`), and a cached actionable state older than 24h is downgraded to `Unknown`
rather than trusted indefinitely.

## Error handling

The client maps `RepositoryError` from HTTP status, connectivity, and body-parsing failures
(`Offline`, `Timeout`, `Http`, `Contract`, `AccessValidation`, `Unexpected` —
`domain/repository/RepositoryResult.kt`). The backend does not need to match this taxonomy
directly, but should:

- Return normal HTTP status codes (4xx/5xx) rather than 200 with an error payload, so the client's
  transport-level error mapping applies.
- Never include the OpenAlex key, stack traces, or other backend-internal detail in an error body —
  the client surfaces error messages toward the user, and `RepositoryError.userMessage` assumes
  server errors are already safe to summarize.
