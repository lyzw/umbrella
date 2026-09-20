# Parent Family Menu Editor Implementation Plan

> **For agentic workers:** Implement task-by-task and verify each task before continuing.

**Goal:** Allow a parent to select catalog dishes and publish a daily menu that is visible only inside the parent's current family.

**Architecture:** Keep the existing family-menu write path and its server-derived ownership. Add parent-only read endpoints for the available dish catalog and the current family's raw menu, then extend the existing mini-program menu page with a parent editing mode while leaving the child ordering flow intact.

**Tech Stack:** Spring Boot, MyBatis-Plus, JUnit/MockMvc, WeChat Mini Program JavaScript/WXML/WXSS, Node test runner.

## Global Constraints

- Follow existing project style and make the smallest complete change.
- Do not add third-party dependencies or change database fields.
- Validate parameters and return existing business errors.
- Never accept `familyId`, `ownerKey`, or `sourceType` from the parent client.
- Do not perform Git operations.

---

### Task 1: Parent catalog and current-family menu reads

**Files:**
- Modify: `growth-planet/src/main/java/cn/studykid/growthplanet/controller/MenuController.java`
- Modify: `growth-planet/src/main/java/cn/studykid/growthplanet/service/CatalogService.java`
- Create: `growth-planet/src/main/java/cn/studykid/growthplanet/dto/response/MenuMaintenanceResp.java`
- Modify: `growth-planet/src/test/java/cn/studykid/growthplanet/CatalogFlowIT.java`

**Interfaces:**
- `GET /api/parent/dish?page=1&pageSize=100&categoryId=&keyword=` returns only `ON_SALE` dishes to a bound parent.
- `GET /api/parent/menu-daily?menuDate=YYYY-MM-DD&mealType=LUNCH` returns the menu belonging to the authenticated parent's current family, including unavailable and missing references needed for maintenance.
- `POST /api/parent/menu-daily` remains the write interface and continues deriving family ownership from authentication.

- [x] Add failing integration coverage for parent-only catalog access, no-child menu reads, and cross-family isolation.
- [x] Add a parent authorization helper that validates the current family and bound parent membership.
- [x] Add the parent catalog query with existing pagination and keyword/category validation.
- [x] Add the current-family menu query without requiring a child profile or consent.
- [ ] Run `mvn -s mvn-settings.xml -Dtest=CatalogFlowIT test` in an environment with Docker/Testcontainers. JDK 21 compilation succeeded locally, but the test environment was unavailable.

### Task 2: Mini-program parent menu editor

**Files:**
- Modify: `growth-planet-miniapp/miniprogram/pages/menu/index.js`
- Modify: `growth-planet-miniapp/miniprogram/pages/menu/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/pages/menu/index.wxss`
- Modify: `growth-planet-miniapp/tests/pages.test.js`

**Interfaces:**
- Parent catalog load paginates through `/parent/dish`.
- Parent menu load uses `/parent/menu-daily`; an unpublished selection returns `data: null`, with legacy `E-404` responses still accepted by the mini program.
- Parent publish sends only `{menuDate, mealType, dishIds, status:"PUBLISHED"}`.

- [x] Add page tests for parent loading, selection limits, payload whitelist, and child-flow regression.
- [x] Split page loading by role so parent maintenance does not require a bound child.
- [x] Render parent selection controls, selected counts, invalid-reference cleanup, and publish action.
- [x] Preserve child favorites, quantities, checkout, safety filtering, and school/family source behavior.
- [x] Run `node --test tests/*.test.js`, `node scripts/check.mjs`, and `node scripts/check.mjs --native`.

### Task 3: Contract and verification notes

**Files:**
- Modify: `growth-planet/docs/sprint3-catalog.md`
- Modify: `growth-planet-miniapp/README.md`
- Modify: `docs/成长星球_V0.0.1_开发进度.md`

- [x] Document the two parent read endpoints and request/response examples.
- [x] Remove the delivered editor from the known-gap lists without claiming real-device acceptance.
- [ ] Run the focused backend and mini-program suites again after documentation updates. Mini-program checks and JDK 21 packaging passed; the backend focused integration suite remains blocked by the unavailable Docker/Testcontainers environment.
