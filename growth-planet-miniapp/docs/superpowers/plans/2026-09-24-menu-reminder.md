# 今日餐单提醒家长 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当儿童发现今天没有家庭餐单时，可通过首页空状态入口创建一条幂等的家长站内待办提醒。

**Architecture:** 复用后端现有 `NoticeService.recordEvent` 的事务、收件人权限和订阅降级机制，新增儿童专用提醒服务与接口，不新增数据库结构。小程序只在家庭午餐为空时展示按钮，点击后调用接口并根据服务端状态反馈，重复点击不会产生重复通知。

**Tech Stack:** Spring Boot、MyBatis-Plus、Java 17、原生微信小程序 JavaScript/WXML/WXSS、Node.js built-in test runner。

## Global Constraints

- 不调用不存在的微信订阅授权或发送接口；站内通知是本批实际交付能力。
- 服务端不信任客户端日期、家庭 ID 或家长 ID；日期使用 `BusinessTime` 的 `Asia/Shanghai`。
- 只允许当前已绑定儿童提醒其家庭创建家长；无有效家庭上下文时返回现有权限错误。
- 幂等键按 `childId + businessDate` 生成，同一天同一儿童最多一条提醒。
- 不新增数据库字段、索引或第三方依赖。
- 保持现有原生小程序请求、错误处理和 `ui.run` 约定。

---

### Task 1: 后端儿童餐单提醒接口

**Files:**
- Create: `growth-planet/sk-growthplanet-core/src/main/java/cn/studykid/growthplanet/service/MenuReminderService.java`
- Create: `growth-planet/sk-growthplanet-miniapp/src/main/java/cn/studykid/growthplanet/controller/MenuReminderController.java`
- Create: `growth-planet/sk-growthplanet-core/src/main/java/cn/studykid/growthplanet/dto/response/MenuReminderResp.java`
- Test: `growth-planet/sk-growthplanet-start/src/test/java/cn/studykid/growthplanet/MenuReminderFlowIT.java`

**Interfaces:**
- Consumes: authenticated `CHILD` request; `UserContext.userId()` and `UserContext.familyId()`; `FamilyMapper`, `FamilyMemberMapper`, `NoticeMapper`, `NoticeService`, `BusinessTime`.
- Produces: `POST /api/mini/child/menu-reminder` with an empty JSON object or no body; response `{"status":"CREATED"}` on first reminder and `{"status":"ALREADY_EXISTS"}` for the same child/date after that.
- Event contract: `eventKey = "menu-reminder:" + childUserId + ":" + businessDate`, `eventType = "MENU_REMINDER"`, `familyId = bound family`, `childId = current child`, `receiverId = family.ownerUserId`.

- [x] **Step 1: Write the failing integration test**

```java
@Test
void childCanRemindBoundFamilyOwnerOncePerBusinessDay() throws Exception {
    var ctx = boundFamily();

    mockMvc.perform(post("/api/mini/child/menu-reminder")
            .header("Authorization", bearer(ctx.childToken()))
            .contentType(JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CREATED"));

    mockMvc.perform(post("/api/mini/child/menu-reminder")
            .header("Authorization", bearer(ctx.childToken()))
            .contentType(JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ALREADY_EXISTS"));

    assertEquals(1, notices.selectCount(new QueryWrapper<Notice>()
            .eq("event_type", "MENU_REMINDER")
            .eq("receiver_id", parentId(ctx))
            .eq("channel", "IN_APP")));
}
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw -pl sk-growthplanet-start -Dtest=MenuReminderFlowIT test` from `growth-planet`.

Expected: FAIL because the controller and service do not exist yet.

- [x] **Step 3: Implement the response and service**

```java
@Data
@Builder
public class MenuReminderResp {
    private String status;
}
```

`MenuReminderService.remindParent()` must:

1. Require a logged-in child and a non-null current family ID.
2. Load a non-deleted `FamilyMember` for the current user, current family, role `CHILD`, and bind status `BOUND`; otherwise throw `E-009`.
3. Load the non-deleted family and require a positive owner user ID; otherwise throw `E-009`.
4. Build the event key from the current child user ID and `BusinessTime.today()`.
5. Query the owner's `IN_APP` row for that event key. Return `ALREADY_EXISTS` when found.
6. Call `NoticeService.recordEvent` inside the service transaction to create both channel rows atomically.
7. Return `CREATED`.

- [x] **Step 4: Add the authenticated controller**

```java
@RestController
@RequestMapping("/api/mini/child")
public class MenuReminderController {
    private final MenuReminderService service;

    public MenuReminderController(MenuReminderService service) {
        this.service = service;
    }

    @PostMapping("/menu-reminder")
    @RequireRole(RoleEnum.CHILD)
    public Result<MenuReminderResp> remindParent() {
        return Result.ok(service.remindParent());
    }
}
```

- [x] **Step 5: Add boundary and idempotency assertions**

Cover child without a bound family, parent calling the child endpoint, and a same-key concurrent request producing one `IN_APP` row. Assert the `SUBSCRIBE` row remains `UNAUTHORIZED` and no platform delivery is claimed.

- [ ] **Step 6: Run the focused backend test**

Run: `./mvnw -pl sk-growthplanet-start -Dtest=MenuReminderFlowIT test`.

Expected: PASS with no schema migration required.

### Task 2: 小程序首页空状态入口

**Files:**
- Modify: `growth-planet-miniapp/miniprogram/pages/home/index.js`
- Modify: `growth-planet-miniapp/miniprogram/pages/home/index.wxml`
- Modify: `growth-planet-miniapp/miniprogram/pages/home/index.wxss`
- Test: `growth-planet-miniapp/tests/pages.test.js`

**Interfaces:**
- Consumes: `POST /child/menu-reminder`, `data.status`, existing `ui.run` and `busy` handling.
- Produces: only when `familyCount === 0` and the user is a child, a visible empty-state action with `bindtap="remindParent"`.

- [x] **Step 1: Add failing page tests**

```js
test('孩子首页家庭餐单为空时可以提醒爸妈，重复结果给出明确反馈', async () => {
  const page = loadPage('home', 'CHILD');
  const calls = [];
  api.post = async (endpoint, body) => {
    calls.push({ endpoint, body });
    return { status: calls.length === 1 ? 'CREATED' : 'ALREADY_EXISTS' };
  };

  await page.remindParent();
  assert.deepEqual(calls, [{ endpoint: '/child/menu-reminder', body: {} }]);
  assert.equal(page.data.reminderStatus, 'CREATED');

  await page.remindParent();
  assert.equal(page.data.reminderStatus, 'ALREADY_EXISTS');
});
```

- [x] **Step 2: Run the focused page test and verify it fails**

Run: `node --test tests/pages.test.js --test-name-pattern="提醒爸妈"`.

Expected: FAIL because the page has no `remindParent` handler or status field.

- [x] **Step 3: Implement the page action**

Add `reminderStatus: ''` to page data. `remindParent()` must call `api.post('/child/menu-reminder', {})` through `ui.run`, store the returned status, and show a non-blocking toast:

- `CREATED`: `已提醒爸妈`
- `ALREADY_EXISTS`: `今天已经提醒过爸妈啦`
- Other successful payloads: `提醒已发送`

On request failure, preserve the existing `ui.run` error display and do not mark the reminder as successful.

- [x] **Step 4: Render the empty-state action**

Keep the existing recommendation empty copy, add a separate action only when `familyCount === 0`:

```xml
<view wx:if="{{!familyCount}}" class="menu-empty-reminder">
  <text class="menu-empty-reminder-title">今天还没有家庭餐单</text>
  <text class="menu-empty-reminder-copy">提醒爸妈发布今天的餐单吧</text>
  <button class="secondary" bindtap="remindParent" disabled="{{busy || reminderStatus === 'CREATED' || reminderStatus === 'ALREADY_EXISTS'}}">
    {{reminderStatus === 'CREATED' || reminderStatus === 'ALREADY_EXISTS' ? '今天已提醒' : '提醒爸妈发布'}}
  </button>
</view>
```

The button must remain readable at narrow widths and must not replace the school meal entry.

- [x] **Step 5: Run all miniapp checks**

Run:

```bash
node --test tests/*.test.js
node scripts/check.mjs
node scripts/check-recipe-parity.mjs
git diff --check
```

Expected: all tests and structural checks pass. Native WXML compilation remains dependent on a locally installed WeChat developer tool.

### Task 3: Contract and verification documentation

**Files:**
- Modify: `growth-planet/docs/sprint2-4-api.md`
- Modify: `growth-planet-miniapp/README.md`
- Modify: `growth-planet-miniapp/docs/verification.md`

**Interfaces:**
- Consumes: the endpoint and status contract from Task 1.
- Produces: a documented API example, no claim of actual WeChat subscription delivery.

- [x] **Step 1: Document the request and response**

Add:

```json
{}
```

```json
{"code":0,"data":{"status":"CREATED"},"message":"success","requestId":"example"}
```

Document `ALREADY_EXISTS`, family/role checks, daily idempotency, and the fact that the `SUBSCRIBE` row is not proof of WeChat delivery.

- [x] **Step 2: Record verification boundaries**

State that the batch verifies in-app notification persistence and UI feedback, while real device behavior, platform authorization, and external WeChat delivery remain outside this batch.

- [x] **Step 3: Run the combined verification**

Run the focused backend test, all miniapp tests/checks, and inspect `git diff --check` before reviewing the batch for unrelated changes.
