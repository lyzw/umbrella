# Growth Planet Miniapp UI Redesign Implementation Plan

> **For agentic workers:** Execute inline in the current workspace. The user explicitly requested no git operations.

**Goal:** Redesign the Growth Planet miniapp into a modern, child-friendly and parent-trustworthy interface without changing backend contracts or business rules.

**Architecture:** Keep the native WeChat miniapp structure and existing page JavaScript. Establish shared visual tokens in `app.wxss`, then update page templates and page-level styles around the existing data and event bindings. Preserve role-based blue/green theming, accessibility targets, error states, and all permission boundaries.

**Tech Stack:** Native WeChat Mini Program, JavaScript, WXML, WXSS, Node.js built-in tests.

## Global Constraints

- Follow existing project conventions and make the smallest viable changes.
- Do not add third-party dependencies.
- Do not change API payloads, response handling, authorization, money calculation, or persistence.
- Keep touch targets at least 44px and preserve readable contrast.
- Do not perform git operations.

---

### Task 1: Shared Visual System and Navigation

**Files:**
- Modify: `growth-planet-miniapp/miniprogram/app.json`
- Modify: `growth-planet-miniapp/miniprogram/app.wxss`
- Modify: `growth-planet-miniapp/miniprogram/components/app-nav/index.js`
- Modify: `growth-planet-miniapp/miniprogram/components/app-nav/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/components/app-nav/index.wxss`

**Deliverable:** Introduce role-aware color, spacing, typography, surface, button, form, status, and motion tokens; replace emoji navigation with stable text glyphs and clearer active states.

**Verification:** Run native WXSS/WXML compilation and inspect the 375px simulator.

### Task 2: Login and Home Experience

**Files:**
- Modify: `growth-planet-miniapp/miniprogram/pages/login/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/pages/login/index.wxss`
- Modify: `growth-planet-miniapp/miniprogram/pages/home/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/pages/home/index.wxss`

**Deliverable:** Build a recognizable Growth Planet brand scene, equal-weight role selection, a clearer child/parent home hierarchy, modern quick actions, and improved empty/growth/profile states.

**Verification:** Confirm all existing event handlers and WXML conditions remain present, then compile and inspect both role layouts.

### Task 3: Core Meal and Approval Flow

**Files:**
- Modify: `growth-planet-miniapp/miniprogram/pages/menu/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/pages/menu/index.wxss`
- Modify: `growth-planet-miniapp/miniprogram/pages/confirmation/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/pages/confirmation/index.wxss`

**Deliverable:** Improve filtering, dish cards, safety labels, quantity controls, sticky checkout, confirmation summaries, approval hierarchy, and retry/error presentation while preserving all workflow conditions.

**Verification:** Run page behavior tests and native compilation.

### Task 4: Wallet, Family, Profile, Notices, and Privacy

**Files:**
- Modify: the WXML/WXSS files under `pages/wallet`, `pages/family`, `pages/profile`, `pages/notices`, and `pages/privacy`

**Deliverable:** Apply consistent page headers, summaries, section hierarchy, status rows, forms, empty states, and action placement across support pages.

**Verification:** Check long text, empty records, disabled actions, parent/child themes, and 320/375/430px widths.

### Task 5: Regression and Visual Verification

**Files:**
- Modify only if verification exposes a concrete issue.

**Steps:**
- Run `node --test tests/*.test.js`.
- Run `node scripts/check.mjs`.
- Run `node scripts/check.mjs --native`.
- Recompile in WeChat DevTools.
- Inspect login, home, menu, confirmation, wallet, and settings states in the simulator.
- Confirm no API, database, or business JavaScript changes were introduced.

