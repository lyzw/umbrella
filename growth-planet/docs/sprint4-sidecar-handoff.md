# Notice And Privacy Sidecar

Date: 2026-09-18. Integrated and verified in the final 13:36:24 run: 100 tests
passed, including notice 9 and privacy 4. Favorite ID export assertions cover
empty arrays and large IDs as strings. See the [verification record](sprint2-4-verification.md).
All data was synthetic and isolated; no business database or external WeChat
calls were made. This is not production acceptance or a full data-rights solution.

## Integration

- Keep `NoticeService.recordEvent(String eventKey, String eventType, Long familyId,
  Long childId, Long receiverId)` inside the caller transaction. The class requires
  `Propagation.MANDATORY`. Both channel rows roll back with the business operation.
  Same key/receiver with changed type or scope returns E-012.
- `recordBinding` and `cancelSubscriptions` remain available. Cancellation changes
  pending subscription rows to UNAUTHORIZED, clears their retry time and preserves
  attempts. IN_APP records do not depend on subscription acceptance.
- Apply `sql/sprint4_schema.sql` once after all other sprint schemas.
- Parent supplies `ResultCode.E410_GONE`; no other shared error changes are needed.
- Run `NoticeCenterFlowIT` (9 tests), `PrivacyWorkflowIT` (4 tests), then the existing
  regressions under the parent's serialized test run. Two existing
  `ComplianceFlowIT` export calls now supply the mandatory idempotency key.
- `MigrationIT` compares all schema metadata after rebuilding Sprint 1 notice and
  privacy tables. Isolate it to the Sprint 1 initializer, or explicitly reconcile
  its fixture with later additive schemas. Do not blindly rerun Sprint 4 against
  already-existing sidecar tables.

## API

All endpoints use existing authenticated sessions and unified responses, except
the download endpoint which returns a JSON attachment. Resource IDs are strings
in responses.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/api/notices` | Own IN_APP rows; page >= 1, pageSize 1..100 (default 20), optional unreadOnly |
| GET | `/api/notices/unread-count` | Own unreadCount |
| POST | `/api/notices/{id}/read` | Own IN_APP row, idempotent |
| POST | `/api/notices/subscription` | Own SUBSCRIBE row only; noticeId and accepted |
| POST | `/api/compliance/data-export` | Existing childId input; Idempotency-Key now mandatory |
| POST | `/api/compliance/data-delete` | childId, confirmed:true, fresh WeChat code; mandatory Idempotency-Key |
| GET | `/api/compliance/requests/{id}` | Requester or explicitly allowlisted ADMIN; audited |
| POST | `/api/admin/compliance/requests/{id}/transition` | Allowlisted ADMIN only; versioned, audited workflow |
| GET | `/api/compliance/requests/{id}/download` | Requester PARENT only; active scope, READY EXPORT, 24-hour expiry; expired => 410/E-410 |

Subscription request and response:

```json
{"noticeId":"102","accepted":true}
```

```json
{"code":0,"data":{"status":"PENDING"},"message":"success","requestId":"example"}
```

Delete request (send `Idempotency-Key: delete-001`):

```json
{"childId":"1001","confirmed":true,"code":"fresh-wx-code"}
```

Intake response (dueAt is not fabricated before operator assignment):

```json
{"code":0,"data":{"taskId":"301","status":"RECEIVED","requestType":"DELETE","dueAt":null,"errorCode":null,"expiresAt":null,"version":0,"downloadAvailable":false},"message":"success","requestId":"example"}
```

Operator acceptance input:

```json
{"expectedVersion":0,"status":"PROCESSING","dueAt":1790000000000,"evidenceRef":"ticket_123"}
```

`dueAt` must be in the future when the request is submitted. PROCESSING requires
an explicit dueAt; subsequent transitions reject a supplied dueAt. Every
transition requires an opaque evidenceRef matching `[A-Za-z0-9_-]{1,128}`.
No path or download object URL can be submitted.

Export release input:

```json
{"expectedVersion":1,"status":"READY","evidenceRef":"ticket_123"}
```

DELETE can move from PROCESSING to COMPLETED with evidenceRef, but this only
records an operator's externally evidenced completion; this module never deletes
the child's data. EXPORT cannot use that transition. FAILED/REJECTED require one
of IDENTITY_UNCONFIRMED, OUT_OF_SCOPE, MANUAL_REVIEW_FAILED as errorCode.

## Delivery Boundaries

- Default `notice.delivery-enabled=false`; scheduler is absent unless enabled.
  Readiness additionally requires a nonempty platformApprovalReference, an event
  template mapping and `NoticeTransport.isConfigured() == true`.
- The bundled adapter is intentionally unsupported and always returns false for
  readiness. It never sends and never reports SENT. A real bounded-time WeChat
  adapter, credential/token handling, official API validation, template approval,
  frontend platform authorization and platform acceptance integration are OPEN.
- Authorization is a durable per-event account declaration with a 24-hour local
  validity window, not proof that the WeChat platform granted authorization. It
  is bound to the latest guardian consent record. Actual platform acceptance
  remains the transport's responsibility.
- A scan handles at most 100 rows (default 50). Family, child, relation/consent,
  notice and subscription locks serialize competing workers and consent revoke.
  Successful revoke prevents later pending sends; a send already holding those
  locks completes before revoke can commit.
- Exceptions become fixed TRANSPORT_FAILURE, never external messages or payloads.
  Initial failure retries at 1, 5, 30 minutes, then FAILED after attempt 4.
  Reauthorization preserves attempts and backoff. Explicit platform rejection is
  terminal. Closed collection, expired/revoked consent or invalid authorization
  cancels without calling transport.
- External calls hold the authorization locks. The future transport MUST enforce
  its own short network timeouts; the 20-second transaction timeout is not a
  network cancellation mechanism. Latency/lock-contention testing is OPEN.
- A process crash or DB commit failure after external platform acceptance can
  cause redelivery. No exactly-once or delivered/read guarantee is made. SENT
  means platform accepted, not that a person received or read it.

## Privacy Boundaries

- DELETE compares the existing WechatClient session openid to the authenticated
  account before new intake. Only a code SHA-256 digest is persisted for replay
  prevention; no raw code/session key is stored. verifiedAt records account
  reauthentication, not verified guardianship. Same-key/same-payload retries
  return the existing request; changed type/child scope returns E-012.
- `privacy.operator-ids` defaults empty. Both ADMIN role and explicit configured
  membership are required; ordinary ADMIN has no automatic privacy privilege.
- READY exports last 24 hours from release. Response status is derived as EXPIRED
  from the persisted expiresAt even without a cleanup job; no file is retained.
- Download generates current `PROFILE_RIGHTS_V1` JSON in memory: scoped current
  profile/preferences and at most the requester's last 500 consent metadata rows.
  Preferences include `favoriteDishIds` as decimal strings (unset becomes `[]`).
  It declares its scope and consent truncation. It is NOT a point-in-time package,
  NOT a full historical export, and excludes wallet/order/account credential data.
- Generated bodies are not stored in DB or files, not logged, and returned with
  private/no-store, attachment and nosniff headers. Only a fixed format marker is
  saved in resultRef. Authenticated download rechecks active parent/child scope
  even after release; consent withdrawal itself does not remove rights access.
- TLS, reverse-proxy/body logging controls, browser/device copies, memory dumps,
  rate limits, evidence-ticket verification and access reviews remain operational
  security gates. Evidence IDs attest to external work; this module cannot verify
  that an external deletion was actually performed.
- Full export coverage, manual handling owner/calendar/SLA, deletion across DB,
  caches, indexes and existing files, backup restoration/deletion replay, and
  retention/purge approval for notices, authorization, verification digests and
  audit rows remain OPEN. No automatic destruction or backup deletion exists.

## Schema And Rollout

The schema is additive, not repeatable: it adds notice read/retry columns and
indexes, a unique per-notice subscription table, operator/evidence/version fields,
a stronger `(requester_id,idempotency_key)` key, and a code-digest replay table.
There are no foreign-key cascades or payload/file columns.

Before adding the stronger key to existing data, this query must return no rows:

```sql
SELECT requester_id, idempotency_key, COUNT(*) AS duplicate_count
FROM sys_privacy_request
GROUP BY requester_id, idempotency_key
HAVING COUNT(*) > 1;
```

Do not delete or rename conflicting historical keys without reviewed
reconciliation. Existing SUBSCRIBE rows do not acquire authorization by migration;
do not mark them SENT or queue them by bulk update. Existing notices start unread
and attemptCount=0. Inventory row counts, orphan scopes and privacy requests;
rehearse DDL index-build lock times and rollback against isolated copies.
Application rollback retains additive columns, keys and audit/auth evidence.
No production migration, cleanup or restoration rehearsal has been executed.
