# Child Home Menu Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the duplicate child-home ordering button and make the lunch summaries link to the correct menu source.

**Architecture:** Keep existing home data requests and menu submission checks. Pass an allowlisted source through navigation, consume it once after the menu role guard, and ignore it for parent maintenance and resubmission.

**Tech Stack:** WeChat Mini Program JS, WXML, WXSS; node:test.

## Global Constraints

- No new dependencies, API changes or database changes.
- Preserve unrelated worktree changes.
- Keep SCHOOL read-only and previous-confirmation resubmission on FAMILY.
- Verify native compilation when the local compiler is available; do not equate it with device rendering.

---

### Task 1: Source Navigation and Home Layout

**Files:**
- Modify: `miniprogram/pages/home/index.js`, `index.wxml`, `index.wxss`.
- Modify: `miniprogram/pages/menu/index.js`.
- Test: `tests/pages.test.js`.

**Interfaces:**
- Home `openMealSource(event)` accepts dataset `source` in `FAMILY` / `SCHOOL`, only for CHILD.
- Menu `onLoad(query)` stores a validated `sourceType`; `onShow()` consumes it once.
- Existing `/menu/daily` continues to receive `sourceType`, current date and `mealType: LUNCH`.

- [x] Add failing page tests for both links, invalid source, parent role, busy state, menu initial source, one-shot consumption and FAMILY resubmission precedence.
- [x] Run `node --test --test-name-pattern='首页餐单|餐单来源' tests/pages.test.js` and confirm missing behavior fails.
- [x] Implement the navigation allowlist and one-time menu initialization. Return the existing `ui.run` promise from menu `onShow` for lifecycle testing.
- [x] Replace the summary's duplicate CTA with two equal-width source buttons. Use four-character lunch labels, wrapping counts and nonwrapping labels. Show recommendation headings only when their rows exist.
- [x] Run `node --test tests/*.test.js`, `node scripts/check.mjs --native`, `node scripts/check-recipe-parity.mjs` and `git diff --check`.
- [ ] Commit only these files and this plan after reviewing the diff.

> Verification note: `check.mjs --native` reached the structure check, but native WXML compilation was unavailable because the local WeChat DevTools `wcc` executable is not installed.

**Manual Acceptance:** In DevTools verify 320px and 390px widths, zero/multiple dishes, both source links, parent maintenance unchanged, and school view cannot submit a family confirmation.

**Next Batch:** Add earned/total medal progress and a consistent unearned progress label. Keep reminder delivery separate until its backend authorization and deduplication contract is defined.
