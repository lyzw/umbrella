# Sprint 1 Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 growth-planet 的 Sprint 1 审计问题，对齐修订后的身份、同意、档案、事件和权利请求契约。

**Status（2026-09-18复核）:** Task 1至5已完成、已验证；仅关闭本计划后端审计修复范围，不关闭开发计划的整个 Sprint 1 / M1 或发布闸门。

**Architecture:** 保留 Controller/Service/Mapper 分层和现有依赖。数据库会话版本负责 token 失效；授权服务统一家庭、儿童账号、关系的加锁顺序。同意历史只追加，业务状态、通知事件和成功审计同事务提交。

**Tech Stack:** Java 21、现有 Spring Boot / MyBatis-Plus / Jackson / MySQL / JUnit / Testcontainers。

## Global Constraints

- 不执行 Git 操作，不升级或新增第三方依赖。
- 不连接或迁移用户现有数据库；DDL 仅供审查，数据库测试仅使用 Testcontainers 的隔离库。
- 无有效同意不可建档；过敏字段仅家长维护；撤回后保留权利请求入口。
- API 使用 `{code,data,message,requestId}`，业务 ID 序列化为字符串，访问 token 为30分钟。
- Q-01 未批准时生产真实数据入口保持关闭；测试只用合成数据。前端五 Tab、钱包、菜单、实际订阅投递和隐私办理不是本次扩展范围。
- 本会话直接执行下列步骤，不创建新任务、不提交代码。上方通用技能模板的工作流名称不代表这些可选技能已安装。

## Task 1: 启动、校验和响应

**Files:** `growth-planet/src/main/java/cn/studykid/growthplanet/{util/JwtUtil.java,entity/ChildProfile.java,common/result,common/exception,config,dto}`；`growth-planet/src/test/java/cn/studykid/growthplanet/InfrastructureRegressionTest.java`。

**Interfaces:** 保留 `Result.ok/fail`；错误码改为字符串，成功码保留0。保留 `JwtUtil(JwtProperties)` 和测试构造器。

- [x] 新增测试：Spring 创建 JwtUtil；ChildProfile 存在自动 ResultMap；空档案和 agreed=false 校验失败；错误响应含 requestId。
- [x] 运行 `mvn -o -s mvn-settings.xml -Dtest=InfrastructureRegressionTest test`，记录旧实现失败。
- [x] 明确注入构造器，启用 JSON ResultMap，补必填/长度/集合/年龄约束，修复异常映射和 trace 上下文。
- [x] 运行该测试，确认所有断言通过。

测试断言使用：

```java
assertNotNull(context.getBean(JwtUtil.class));
assertTrue(table.isAutoInitResultMap());
assertFalse(validator.validate(new ChildProfileReq()).isEmpty());
assertNotNull(Result.fail(ResultCode.E009_FORBIDDEN).getRequestId());
```

## Task 2: 会话与授权

**Files:** `common/context/LoginUser.java`、`entity/User.java`、`mapper/UserMapper.java`、`interceptor/JwtInterceptor.java`、`service/SessionService.java`、`service/ChildAuthorizationService.java`、`service/impl/AuthServiceImpl.java`、`controller/AuthController.java`。

**Interfaces:** `SessionService.authenticate(String)` 返回最新 LoginUser；`ChildAuthorizationService.lockChild(Long, Long)` 返回锁定的 FamilyMember；同意检查返回 ConsentLog。

- [x] 添加角色重复/切换、旧版本 token、禁用用户和登录家庭恢复的测试。
- [x] 新增 `token_version`，首次角色选择原子递增，退出使全部已有会话失效；校验最新角色、状态、关系。
- [x] 实现家庭→儿童账号→关系锁；仅家长在 BOUND + 有效 PROFILE 同意后保存完整档案。
- [x] 校验生产密钥与 Q-01 配置，默认禁止真实微信账号处理。
- [x] 运行对应单元测试。

## Task 3: 绑定、同意与通知

**Files:** `service/impl/{FamilyServiceImpl,ComplianceServiceImpl}.java`、`service/NoticeService.java`、`entity/{ConsentLog,FamilyMember,Notice}.java`、对应 Mapper / DTO / Controller。

**Interfaces:** `NoticeService.recordBinding(FamilyMember)` 在当前事务写入站内和订阅待发记录；同意按 parent/child/type/version/apply-generation 留痕。

- [x] 测试不同意审批、跨家庭同意、重复审批、拒绝重申及撤回后写入。
- [x] 申请仅保存最小关系；重申更新原关系并递增申请版本，旧同意不复用。
- [x] 仅允许 PENDING 儿童审批，检查当前协议同意；角色、绑定、声明状态分离。
- [x] 同意/撤回只追加事件，重复请求幂等；撤回取消该授权范围的待投递消息。
- [x] 增加事件失败回滚、撤回/写入锁竞争的隔离 MySQL 集成测试。

## Task 4: 隐私请求与审计

**Files:** `entity/PrivacyRequest.java`、`mapper/PrivacyRequestMapper.java`、`service/impl/ComplianceServiceImpl.java`、`service/impl/AuditServiceImpl.java`、`controller/ComplianceController.java`。

**Interfaces:** `dataExport(DataExportReq, String)` 返回持久化 taskId 和 RECEIVED；请求查询只允许发起家长。

- [x] 测试导出同键同参返回原任务、同键异参409、跨家庭拒绝、撤回后仍可受理。
- [x] 建立 durable request 和 request hash；不返回内联儿童数据，不宣称已生成导出文件。
- [x] 审计包含 requestId、对象、授权版本、结果/错误码；拒绝日志不记录请求正文或儿童资料。
- [x] 验证成功审计和业务原子提交，失败审计独立事务并有安全的错误日志兜底。

## Task 5: Schema、迁移和验收

**Files:** `growth-planet/sql/sprint1_schema.sql`、`growth-planet/sql/migrations/`、`growth-planet/src/test/resources/schema-test.sql`、`growth-planet/src/test/java/cn/studykid/growthplanet/*IT.java`、`growth-planet/docs/`、`growth-planet/README.md`。

- [x] 同步新建库 schema 和测试 schema，身份唯一键不因软删除复用。
- [x] 提供迁移预检、增量 DDL、旧状态保守回填及重跑/备份/停写/回滚注意事项，不能自动合并重复身份或认可旧同意。
- [x] 修正集成测试家庭 token 和申请→同意→绑定→家长建档顺序。
- [x] 执行全部单元测试；Docker 可用时仅运行隔离集成库测试，记录环境限制。
- [x] 更新架构、时序、README、API JSON 示例和审计项验证矩阵；撤下旧 PASS 声明。

## Verification Record

2026-09-18 10:50:30（Asia/Shanghai）最终全量测试43项全部通过：14项单元测试、29项隔离 MySQL/Redis 集成测试。包含并发审批、撤回与写入两种顺序、通知事务回滚、身份不可复用及重建旧结构的迁移校验。

2026-09-18 10:52:09（Asia/Shanghai）`mvn -s mvn-settings.xml package`成功，产物为 `growth-planet/target/growth-planet.jar`。本轮状态复核读取已有测试报告，未重新运行后端测试。

未连接现有业务库、未执行实际迁移、未执行 Git 操作、未升级依赖。发布审批、真实微信/真机联调、生产压测及存量迁移恢复演练不在该结果覆盖范围。详细对应表见 `growth-planet/docs/sprint1-verification.md`。
