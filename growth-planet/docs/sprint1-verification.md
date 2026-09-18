# Sprint 1 审计修复与验证

日期：2026-09-18。范围：`growth-planet` 后端。依据上层审计修订基线；本记录不替代产品、合规、安全及生产发布签核。

## 修复对应表

| 编号 | 问题与修复 | 验证证据 |
| --- | --- | --- |
| 1 | JwtUtil 构造器注入失败；显式注入，并修复自定义请求过滤器与框架 Bean 同名冲突 | InfrastructureRegressionTest；所有 IT 实际启动完整 Spring 上下文 |
| 2 | 公开接口任意切换身份；改为 UNSELECTED 一次选择，相同角色幂等，禁止 ADMIN 自选 | AuthFlowIT、SecurityRegressionIT |
| 3 | 旧 token、重登家庭上下文、禁用和退出不可靠；数据库 token_version + 每请求读取当前账号/BOUND 关系 | SecurityRegressionIT、JwtUtilTest |
| 4 | 缺少前置采集闸门及生产配置保护；默认关闭，演示显式使用合成配置，prod 拒绝混合 profile/演示密钥/合成审批目录 | ProductionConfigGuardTest、SecurityRegressionIT |
| 5 | 儿童在绑定和同意之前建档；仅允许家长在 BOUND + 有效 PROFILE 同意后写入 | ComplianceFlowIT、AuthFlowIT |
| 6 | 儿童可改过敏字段、JSON 回读不完整；档案接口限定 PARENT，校验发布目录，启用 autoResultMap | ComplianceFlowIT 三个数组真实 MySQL 回读；InfrastructureRegressionTest |
| 7 | 同意可伪造、声明与核验混淆；校验 agreed、整数年龄18至120、childId/applyId、家庭、协议和申请版本，仅生成 SELF_ATTESTED | ComplianceFlowIT；核验策略不足、协议更新/过期均阻断写入 |
| 8 | 撤回覆盖同意、错误退出家长；改为追加 REVOKE，重复幂等，保留查询/导出申请，重授追加新 GRANT | ComplianceFlowIT；撤回取消待发订阅并验证 GRANT/REVOKE/GRANT 历史 |
| 9 | 绑定状态与并发约束缺失；PENDING→BOUND/REJECTED，拒绝重申递增申请版本，唯一有效儿童家庭 | FamilyFlowIT、SecurityRegressionIT、TransactionRegressionIT |
| 10 | 绑定通知事件缺失；站内/订阅记录与业务同事务，事件唯一键防重，未授权订阅不伪装已发送 | 8 路并发审批只产生一次业务审计、两条渠道记录；通知落库后注入异常验证全部回滚 |
| 11 | 导出仅即时返回资料、无持久化；改为 RECEIVED 任务、请求摘要及同键重试，只允许发起家长查询 | ComplianceFlowIT：同键同参、同键异参 E-012、跨家庭/跨发起人、撤回后受理 |
| 12 | 缺少失败审计、trace 和敏感数据保护；成功同事务，失败独立事务，记录已认证操作者，日志不包含正文/SQL 参数 | SecurityRegressionIT 的 trace、拒绝 actor 和上下文清理断言；TransactionRegressionIT 回滚断言 |
| 13 | 参数缺失、非法集合/协议/年龄未阻断；补必填、长度、集合、正整数 ID、禁止小数截断，统一安全错误响应 | InfrastructureRegressionTest、ComplianceFlowIT、SecurityRegressionIT |
| 14 | 错误码、ID 类型、TTL 与登录字段不一致；成功0、错误 E-xxx、业务 ID 字符串、时间数字、expiresIn=1800、isNew 明确命名 | InfrastructureRegressionTest JSON 往返；AuthFlowIT |
| 15 | 软删除释放身份及无增量迁移；唯一键保留身份，提供预检/扩展/切换脚本，旧同意不自动认可 | LogicDeleteTest、SecurityRegressionIT；MigrationIT 比较迁移后与新建库的列定义和索引 |
| 16 | 旧测试绕过正确流程、文档宣称未经验证的能力；统一申请→同意→审批→家长建档流程，测试直接复用正式建表脚本 | 更新 README、架构、类图、时序图；移除重复测试 schema 与旧验收声明 |
| 17 | 家长无法发现审批所需 ID、儿童无法恢复申请状态；新增本家庭儿童关系分页与本人绑定查询 | Sprint1ClosureIT：分页/过滤/越界、跨家庭/角色隔离、NONE/PENDING/REJECTED/BOUND及重申版本 |
| 18 | 缺少家长档案回显；新增 GET，绑定及有效同意前禁止读取 | Sprint1ClosureIT：未绑定403、未建档404、回显白名单、跨家庭拒绝及撤回/重授 |
| 19 | 缺少儿童非安全偏好读写及家长查询；严格字段白名单、本人身份推导、家庭创建家长有效同意 | Sprint1ClosureIT：额外字段/数组边界、仅两个列更新、安全字段不变、家长停用、同意过期/换版、采集关闭 |
| 20 | 新偏好操作须与撤回/审计保持原子性；复用授权锁与事务 | TransactionRegressionIT新增3项：两种撤回/写入顺序、审计失败后偏好及成功审计回滚；故障拒绝保留独立审计 |

## 实测记录

- 环境：本机 JDK 21，沿用项目已有 Maven 依赖；MySQL 8.0.36 / Redis 7.2 为独立 Testcontainers 容器。
- 首轮基础回归曾复现2个断言失败、1个构造器注入错误；后续完整上下文测试发现并修复过滤器同名和 `isNew` JSON 属性问题。
- 2026-09-18 10:50:30（Asia/Shanghai）审计修复阶段全量运行：43项，0失败、0错误、0跳过，包含补充的参数边界、撤回取消待发消息和重新授权断言。
- 上述历史运行构成：14项单元测试，29项隔离集成测试。报告在 `target/surefire-reports/`，重复运行会覆盖。
- 2026-09-18 10:52:09（Asia/Shanghai）打包成功，产物为 `target/growth-planet.jar`；打包阶段再次通过14项单元测试，集成测试结果以上述独立全量运行为准。
- 首次离线打包因已有 Maven 插件缓存的仓库标识不匹配而失败，随后联网解析项目已配置的构建插件并成功打包；未修改 POM 版本或升级依赖。
- 迁移测试是在空容器里重建的旧六表合成夹具，不是客户存量数据副本；覆盖脚本语法、保守回填、历史记录保留、身份和有效家庭唯一约束，以及与新建库的结构一致性。预检异常结果仍须人工审查。

### Sprint 1 后端收口复验

- 新增路由前，6组新增用例均以404/405复现接口缺失；实现后补充家长停用/同意过期及3项事务用例，共新增10项测试。
- 2026-09-18 11:49:57（Asia/Shanghai）执行 `clean test -Pintegration`：**53项通过，14项单元 + 39项隔离集成，0失败、0错误、0跳过**。本轮重新验证旧用例及合成迁移，不只是读取旧报告。
- 2026-09-18 11:51:53 打包成功，再次通过14项单元测试；`target/growth-planet.jar` 的 Start-Class 为 `cn.studykid.growthplanet.GrowthPlanetApplication`，未包含旧 `com/growthplanet` 业务类。
- 7项 Sprint1ClosureIT + 7项 TransactionRegressionIT 定向测试先通过后，再执行上述全量回归。新包名下完整 Spring 上下文、Mapper 扫描和 JSON 处理均经实际启动验证。
- 本轮新增5个接口，当前共18个：AUTH 7、FAMILY 6、COMPLIANCE 5。后端已具备最小申请发现、绑定、家长建档/回显和儿童非安全偏好闭环；F-022收藏及前端不计入完成范围。
- 技术栈未升级、无新依赖、无新增 DDL/索引或业务数据迁移；未执行 Git 操作。全量测试不连接现有业务库。
- 上层文档脚本语法检查及静态检查通过：631项，11份HTML/5份Markdown。基线、详细设计、开发计划、索引及后端图文已同步；静态检查不替代前端实机验收。

复现命令，在 `growth-planet` 目录运行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export DOCKER_HOST=unix:///Users/zhouwei/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
/opt/homebrew/bin/mvn -o -s mvn-settings.xml test
/opt/homebrew/bin/mvn -o -s mvn-settings.xml -Dapi.version=1.44 clean test -Pintegration
/opt/homebrew/bin/mvn -s mvn-settings.xml package
```

## 迁移与接口风险

1. 未连接、修改或迁移现有业务数据库。存量库不能运行初始化 schema；先按 `../sql/migrations/README.md` 备份、预检、停写并在副本演练。DDL 隐式提交，不可盲目整份重跑。
2. 切换后旧 token 失效，用户需重新登录。旧儿童绑定降为 PENDING，需重新同意/审批，旧档案只保留历史并标记 INCOMPLETE；不能把保留历史解释为重新取得采集授权。
3. 客户端必须同步错误字符串、业务 ID 字符串、isNew、expiresIn、家长建档必填字段、撤回200和导出 RECEIVED 契约；请求/响应 JSON 示例见 `../README.md`。
4. 权利表当前物理字段 `requester_id/idempotency_key` 对应设计中的发起家长/请求键；唯一范围为发起人、EXPORT操作类型和请求键。扩展 DELETE 时须明确跨类型键空间与迁移，不能直接假定完整权利模型已完成。
5. 成功处理依赖 MySQL 授权与审计事务；故障时不会用 Redis 放行。失败审计库不可用只保证安全告警兜底，仍需监控补偿，不能声称数据库日志绝对不丢。
6. 新增 GET/PUT 契约示例见后端 README；偏好请求禁止额外字段，客户端不能复用完整档案请求体。撤回后不仅写入，档案/偏好查询也被阻断；最小关系和权利查询保留。
7. 儿童偏好授权限单家庭创建家长模型。未来多家长/解绑需重新设计同意范围与锁顺序；收藏字段尚未交付，不能把接口中的 dislikes/tastes 视为 F-022 已完成。

## 未关闭项

- Q-01 前置身份处理依据、最低核验强度、协议正文与审批引用，以及 Q-07 独立账号策略，仍需负责人批准。配置检查不能自动证明审批真实性。
- 正式字段目录、保留与清理策略、权利办理人员/工作日历、订阅授权/实际投递/重试、频控和监控告警尚需发布流程验收。
- 当前导出只持久化受理，不生成文件、不下载、不执行删除；`dueAt=null`，不虚构已核验或承诺办理日期。后续办理流程必须补时限和超时告警。
- 儿童非安全偏好已在本轮补齐；前端五 Tab、“我爱吃”、钱包、菜单及完整通知中心仍未交付，不能据此认定 Sprint 1 整体或整版功能齐备。
- 未执行真实微信联调、生产压测、真机验收、实际存量迁移/备份恢复演练及依赖 GA/安全发布审查。
