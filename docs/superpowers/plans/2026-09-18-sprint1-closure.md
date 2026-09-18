# Sprint 1 Backend Closure Implementation Plan

**Goal:** 补齐后端 Sprint 1 的申请发现、状态查询、档案回显和非安全偏好闭环，并同步可验证的任务状态。

**Architecture:** 保留现有 Auth/Family Controller-Service-Mapper 分层。儿童偏好使用家庭、儿童账户、关系、同意记录的事务锁，与撤回串行；新增查询只返回当前操作者有权查看的字段。

**Tech Stack:** 现有 Java 21 / Spring Boot / MyBatis-Plus / MySQL / Testcontainers，不增加依赖。

## Global Constraints

- 基础包沿用当前源码的 `cn.studykid.growthplanet`；构建清理旧产物后验证。
- 不操作 Git、不接触现有业务数据库、不新增数据库字段或索引。
- 仅合成数据隔离测试；前端、真实微信、Q-01/Q-07、生产迁移及完整 Q-06 审核不标记完成。
- 收藏菜品属于 Sprint 3；当前偏好响应只交付 childId/dislikes/tastes，不伪造收藏持久化。

## Task 1: Regression Cases

**Files:** `growth-planet/src/test/java/cn/studykid/growthplanet/Sprint1ClosureIT.java`、`TransactionRegressionIT.java`。

- [x] 新增 HTTP 回归：家长发现申请、分页和状态过滤、儿童查询 NONE/PENDING/REJECTED/BOUND、重申版本、跨角色/家庭拒绝、档案未创建、回显和撤回。
- [x] 新增偏好回归：本人/家长读取、儿童只写 dislikes/tastes、拒绝未知字段/空值/超长集合，过敏/身份字段保持不变。
- [x] 运行新测试确认新增路由在实现前失败：6组测试返回404/405。

最小复现断言：

```java
mockMvc.perform(get("/api/family/children")
        .header("Authorization", "Bearer " + ctx.parentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].applyId").value(ctx.applyId().toString()));
```

## Task 2: Closure APIs

**Files:** 新增 `dto/request/ChildPreferencesReq.java`、`dto/response/ChildProfileDetailResp.java`、`ChildPreferencesResp.java`、`FamilyChildResp.java`、`PageResp.java`；修改 `AuthController.java`、`FamilyController.java`、`AuthService.java`、`FamilyService.java`、对应实现和 `ChildAuthorizationService.java`（均在新基础包内）。

**Interfaces:**

- `GET /api/family/children`：PARENT 当前家庭儿童关系，page 默认1、pageSize 默认20/最多100，bindStatus 可选 PENDING/BOUND/REJECTED；items/total/page/pageSize，按关系 ID 升序。
- `GET /api/family/binding`：CHILD 本人当前有效关系；没有有效关系时返回最近拒绝申请，没有申请返回 NONE；不返回家庭邀请码或其他成员资料。
- `GET /api/child/profile?childId=`：PARENT 当前家庭 BOUND + 有效同意，未建档 404，返回档案业务字段而非实体元数据。
- `GET /api/child/preferences?childId=`：本人 CHILD 或所属家长，BOUND + 有效同意；未建档 409/E-002。
- `PUT /api/child/preferences`：CHILD 本人，dislikes/tastes 均必填，每组最多20项，每项非空且最多64字符；任何额外字段 400。只更新已有 COMPLETE 档案的这两个字段，记录不含偏好正文的审计。

- [x] 实现上述契约；共享同意校验增加明确的同意人参数，仅儿童入口推导有效家庭 owner，不改变既有家长授权语义。
- [x] 偏好更新沿用完整事务锁；撤回先提交则更新409，更新先提交则保留历史且撤回后拒绝后续处理。
- [x] 运行新增回归，全部通过后运行全量隔离测试：14项定向用例通过，再全量53项通过。

## Task 3: Evidence and Documentation

**Files:** `growth-planet/README.md`、`growth-planet/docs/sprint1-verification.md`、`growth-planet/docs/sprint1-architecture.md`、相关类图/时序图、`docs/README.md`、审计基线、详细设计、开发计划及旧计划中的包路径。

- [x] 更新新增接口参数/响应/失败边界示例；区别 F-003 非安全偏好与 F-022 收藏。
- [x] 运行 `mvn -o -s mvn-settings.xml -Dapi.version=1.44 clean test -Pintegration`，仅使用独立容器；2026-09-18 11:49:57，53项通过。
- [x] 运行 `mvn -o -s mvn-settings.xml package`，验证 JAR 启动类为新基础包且无旧包类；11:51:53打包成功。
- [x] 运行 `node docs/check-docs.mjs`，631项通过，覆盖11份HTML、5份Markdown；更新实测数量和已完成任务，保留整体 Sprint 未退出及外部验收清单。

测试环境变量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`、`DOCKER_HOST=unix:///Users/zhouwei/.colima/default/docker.sock`、`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。
