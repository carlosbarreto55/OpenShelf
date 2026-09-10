# PaperReader `/v1` semantic contract

This document is normative together with `paperreader.openapi.yaml`. The OpenAPI document defines
wire shapes; this file defines behavior that JSON Schema cannot express.

## Search inputs and filters

`query` is trimmed and must contain non-whitespace text. A supplied `topic` is also trimmed and
must be non-blank. `sort` and `work_type` are trimmed and compared case-insensitively using a
locale-independent mapping. Unsupported values return `400 INVALID_REQUEST` before an OpenAlex
call begins.

Only `open_access=true` restricts Works to `is_oa:true`. `false` and omission have identical,
unrestricted meaning. `topic` is free text: the server searches OpenAlex Topics with
`search=<topic>&per_page=5`, keeps at most five valid `T<digits>` IDs in OpenAlex order, and ORs
them in the Works `topics.id` filter. No resolved IDs returns a normal empty search without a Works
request.

An absent public cursor starts OpenAlex cursor paging with `*`; any returned cursor is opaque and
is forwarded unchanged. A normal search makes one Works request. A PDF-only search inspects whole
pages until it reaches at least 25 matches, upstream end, four pages, or eight seconds by default.
All values are bounded configuration. All matching papers from a fully inspected page are returned,
even when that makes the response larger than 25. `next_cursor` always resumes after the last fully
inspected page.

Each paper ID occurs at most once per response and first-seen OpenAlex order wins. `total_count` is
the upstream count only when a normal page had no validation drop or duplicate. It is the exact
deduplicated PDF count only after a PDF scan begins at `*` and reaches upstream end. It is `null`
for all incomplete or non-initial PDF scans and whenever local validation changed normal membership.

## Work type and access normalization

OpenAlex `article`, `review`, `preprint`, `book-chapter`, and `dataset` become public `article`,
`review`, `preprint`, `book_chapter`, and `dataset`. Any other non-blank upstream type becomes
`other`; absent or blank input becomes `null`.

Candidate precedence is `best_oa_location`, each `locations` entry in upstream order,
`open_access.oa_url`, then `primary_location`. Duplicate URLs retain their first candidate. A
candidate is actionable only when it is HTTPS, has a host, has no user information, and has no
invalid or expired explicit expiry value.

Consistent open evidence is `is_oa=true`, an `oa_status` of `diamond`, `gold`, `green`, `hybrid`,
or `bronze`, or an OA-marked location without contradictory closed work evidence. A valid PDF then
yields `DIRECT_PDF`; otherwise a valid open landing page yields `OPEN_FULL_TEXT`. Consistent closed
evidence is `is_oa=false` or `oa_status=closed` without contradictory open evidence. It yields
`PAYWALLED` when a valid primary publisher page exists and `NO_FREE_FULL_TEXT` otherwise. Missing
or contradictory evidence, and open evidence without an actionable candidate, yields `UNKNOWN`.

`DIRECT_PDF` means only that OpenAlex supplied structurally valid metadata. The server does not
fetch, probe, verify, or host the remote URL. Reachability and content verification remain the
accepted API-001 limitation.

## Resource and failure policy

An empty search is `200` with `items=[]`. A syntactically valid unknown ID on details, access
refresh, or download is `404 PAPER_NOT_FOUND`. Only an existing paper whose current access is not
`DIRECT_PDF` returns `200` from download with its requested `paper_id` and `descriptor:null`.

OpenAlex connect, read, and request-deadline timeouts are `504 OPENALEX_TIMEOUT`. Other upstream
HTTP, protocol, and connectivity failures are `502 OPENALEX_UNAVAILABLE`. Local outbound-capacity
rejection is `503 OUTBOUND_CAPACITY_EXHAUSTED`. Problem details never include an API key, raw
upstream response, user query, cursor, or candidate URL.

## Compatibility

This is the first supported `/v1` live-integration baseline. Future `/v1` changes must be additive
and backward compatible. Changing an existing field, meaning, requiredness, nullability, status,
enum, or pagination invariant requires a new API version, a documented overlap and deprecation
window, passing provider and Android consumer fixtures, and rollback compatibility during the
support window.
