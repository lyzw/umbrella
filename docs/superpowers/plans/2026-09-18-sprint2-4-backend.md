# Sprint 2-4 Backend Implementation Plan

> **For agentic workers:** Use subagent-driven implementation for independent catalog and notification/privacy modules; integrate and verify each task before recording completion.

**Goal:** Deliver the remaining V0.0.1 backend flows against the audited baseline, without claiming frontend or production acceptance.

**Architecture:** Reuse Spring MVC, MyBatis-Plus, scoped family/child locks, consent checks, transactional audit and notice events. Wallet and confirmation operations share the lock order family, child, relation, confirmation, wallet, rule; catalog validation locks menu and dishes before final state changes.

**Tech Stack:** Existing Java 21, Spring Boot 4.1.1, MyBatis-Plus 3.5.17, MySQL 8 and Redis; no added dependencies.

## Global Constraints

- Base package `cn.studykid.growthplanet`; no unrelated refactors.
- Only isolated Testcontainers synthetic data. Never run migrations against configured business databases.
- Money uses BigDecimal and two-decimal JSON strings; IDs use decimal strings.
- Keep Q-01/Q-06/Q-07, frontend, real WeChat, operational approvals and production drills open.
- Schema additions require explicit migration instructions; existing Sprint 1 migration fixtures stay unchanged.
- All work is local; commits are requested, pushes are not.

## Task 1: Commit Sprint 1

- [x] Review existing diff and whitespace.
- [x] Commit family queries (`64670c4`).
- [x] Commit profile/preferences and regression tests (`c9a1c1c`).
- [x] Commit closure documentation (`e6edb4d`).

## Task 2: Wallet and Ledger

**Files:** `growth-planet/sql/sprint2_schema.sql`; new Wallet/AllowanceRule/AllowanceLog entities and mappers, WalletService, WalletController, request/response DTOs; WalletFlowIT.

**Interfaces:** `WalletService.lockWallet(Long childId, Long familyId)` and `lockRule(Long childId)` require an existing transaction and already-held authorization locks. Confirmation service performs final debit using the same mapper conditional updates and unique ledger constraint.

- [x] Add MySQL tables with unique child wallet/rule, unique grant request scope, unique confirmation debit reference, balance/usage checks.
- [x] Test initial balance 0, grant 50, same-key retry, changed payload E-012, cross-family refusal, invalid money, optimistic rule conflict and rollback.
- [x] Implement authorized balance/rule/log queries, grant and versioned rule update. Validate `0 <= single <= daily <= weekly <= 9999.99`, date windows <=31 days.
- [x] Verify daily and weekly resets independently using Asia/Shanghai dates; resets derive successful debit totals.
- [x] Run focused tests and commit verified changes (`57a4c41`).

## Task 3: Catalog and Favorites

**Files:** `growth-planet/sql/sprint3_catalog.sql`; DishCategory/Dish/MenuDaily entities/mappers; CatalogService and MenuController; favorite extension in ChildProfile and preference response; CatalogFlowIT.

**Interfaces:** CatalogService exposes `validateOrder(Long menuId, FamilyMember member, List<OrderLineReq> items)` returning `ValidatedMenu(MenuDaily menu, List<Dish> dishes)`. Call inside transaction after authorization/confirmation locks; returned dishes follow requested item order and are locked. `OrderLineReq` fields: positive Long dishId, integer quantity 1..9, optional note <=255 characters.

- [x] Test ADMIN-only dish CRUD, FAMILY ownership, SCHOOL non-submission, allergies/UNKNOWN/downlisted dishes, duplicate items and wrong menu date.
- [x] Implement category and dish management, SCHOOL/FAMILY menu upsert preserving IDs, safe menu display and self-only favorite collection.
- [x] Require COMPLETE profile and valid consent for child data access; do not accept family ownership from client.
- [x] Add schema migration and focused tests.

## Task 4: Confirmation and Approval

**Files:** `growth-planet/sql/sprint3_confirmation.sql`; confirmation/item/approval entities/mappers, ConfirmService/ConfirmController, DTOs, ConfirmationFlowIT.

**Interfaces:** Consume CatalogService validation and WalletService locks. Publish notices through `NoticeService.recordEvent(String eventKey, String eventType, Long familyId, Long childId, Long receiverId)` in the caller transaction.

- [x] Test 0 -> grant50 -> submit18 (balance50) -> approve (balance32), price snapshot preservation and immutable original lines.
- [x] Implement submit request hashing, unique operation key, new-ID resubmission, parent queue, owner detail/status and versioned withdraw/reject/modify.
- [x] Implement atomic approval debit, usage, unique ledger, approval, status and dual-channel event. Persist approval command digest for completed retries.
- [x] Add E-011 preview data with wallet/rule/confirmation versions and usageDate; reject stale explicit confirmation.
- [x] Test concurrent approvals, both consent-revoke/approval lock orders, approval/withdraw race, stale date/versions and rollback after actual ledger/approval/notice inserts.
- [x] Capture one settlement date for menu/rule/preview/ledger, reject rollover before debit, and test midnight/week boundaries. Return the parent's child-facing explanation in detail responses.

## Task 5: Notification and Privacy Workflows

**Files:** `growth-planet/sql/sprint4_schema.sql`; NoticeService extension, notice center/controller and subscription services; privacy workflow/controller extensions, tests.

**Interfaces:** Preserve `recordBinding` and `cancelSubscriptions`; new `recordEvent` above. Own-account subscription authorization only. Delivery disabled by default unless platform configuration is explicitly supplied; no fake SENT status.

- [x] Implement own-account paginated notices, unread count and idempotent read.
- [x] Implement persistent per-event authorization declarations, bounded event scanner and retry schedule (1/5/30 minutes, at most three retries), revoke cancellation and sanitized errors. Real platform transport remains open.
- [x] Implement deletion request intake with fresh account reauthentication, audited operator transitions, scoped authenticated limited profile export download with 24-hour expiry.
- [x] Keep actual deletion approval, backup restoration and external identity decisions explicit; no destructive automatic processing.
- [x] Test account isolation, expired downloads, status transitions, failed transport and revoked consent.

## Task 6: Integration and Evidence

**Files:** pom.xml test-resource include, application-test.yml schema list, migration README, backend README, new verification record, audit baseline and development plan.

- [x] Run full isolated suite (100 passed at 2026-09-18 13:36:24 Asia/Shanghai):

```bash
env DOCKER_HOST=unix:///Users/zhouwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home /opt/homebrew/bin/mvn -o -s mvn-settings.xml -Dapi.version=1.44 clean test -Pintegration
```

- [x] Run `mvn -o -s mvn-settings.xml package` with the same JAVA_HOME (passed at 13:36:48).
- [x] Run `node --check docs/check-docs.mjs` and `node docs/check-docs.mjs` (645 checks).
- [x] Record actual test counts, API examples, schema ordering, rollback risks and unclosed gates.
- [x] Commit implementation and documentation in independently understandable batches.

Implementation commits: wallet `57a4c41`, catalog `85d4a9b`, notification/privacy `4ff88ee`, confirmation `b2a7970`. Integration configuration, migration regression and final documentation are grouped in the final evidence commit. These commits do not close the release gates listed above.
