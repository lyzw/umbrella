# Sprint 1 实现说明

更新日期：2026-09-18。依据上层审计修订基线，替代旧版架构中的任意切换角色、儿童先建档、声明即核验、撤回登出家长、软删除身份重建及无证据 PASS 声明。

## 身份与会话

- `UNSELECTED` 只能转为 CHILD/PARENT；相同角色调用幂等，不开放 ADMIN 自选。
- 首次选择角色递增 `usr_user.token_version`；JWT 包含该版本，30分钟有效，不实现 refresh。
- `SessionService` 每次检查数据库账号、状态、最新角色和会话版本，并读取当前 BOUND 家庭。JWT 中旧 family_ids 不作为授权依据。
- 注销递增版本，失效该账号全部会话。软删除身份仍保留唯一 openid，重新登录不会新建身份。
- `RequestContextFilter` 生成 requestId，并在 finally 清除 MDC/用户上下文。Redis 不参与会话与撤回的授权决定。

## 绑定与授权

1. 家长建立家庭，创建自身 PARENT/BOUND 成员；本期一个家长仅支持一个家庭。
2. 儿童以有效邀请码建立 PENDING 关系，只保留最小关联；同一儿童只能有一个有效 PENDING/BOUND 家庭。
3. 家长提交对应儿童、申请、协议版本和 PROFILE 类型的明示同意。SELF_ATTESTED 是自报声明，不冒充 VERIFIED。
4. 审批需匹配 PENDING 儿童申请；通过需当前协议、未过期 GRANT 和配置允许的核验强度。重复同结果无新业务事件，终态冲突409。
5. 只有家长能为 BOUND 儿童写档案。nickname 1至64、school 1至128、发布的 grade/过敏原目录，三个数组必传且最多20项，空数组表示无对应偏好。

拒绝后重申复用原关系 ID，递增 `application_version`。同意记录关联 `apply_id + application_version`；旧申请同意不能授权新一轮绑定。数据库生成列唯一索引补强儿童单家庭约束。

## 事务与撤回

授权写入顺序固定为：家庭行 → 儿童账号行 → 成员关系行 → 最新同意记录。加入申请、同意、审批、档案与撤回使用同一顺序，授权读取采用锁定读。

同意/撤回仅追加历史，重复撤回不新增业务事件；撤回成功返回200/REVOKED，不使家长会话失效。撤回提交后后续档案写入409/E-010；此前已经提交的合法档案保留历史，不能自动逆转。隐私请求/查询仍可用。

MySQL 是最终授权源，不用缓存或旧 JWT 放行。新的写入口必须调用同一授权服务；未来加入多家长、解绑、禁用和更多儿童操作时必须重新检查加锁顺序及撤回范围。

## 基础模型

| 表 | 作用 |
| --- | --- |
| usr_user | 身份、角色、状态及会话版本 |
| usr_family | 家庭和邀请码 |
| usr_family_member | 最小绑定申请、申请版本及绑定/核验状态 |
| usr_child_profile | 家长维护的档案；JSON TypeHandler 自动 ResultMap |
| usr_consent_log | 只追加的同意/撤回及申请证据 |
| sys_audit_log | 成功与拒绝审计、requestId/IP、结果/错误码 |
| sys_notice | 事务内写入的站内记录和订阅事件 |
| sys_privacy_request | 权利申请、幂等摘要、状态及后续办理字段 |

绑定申请和审批与 IN_APP/SUBSCRIBE 记录同事务。唯一键为 `(event_key,receiver_id,channel)`，包含申请版本和状态；当前订阅无授权，记 UNAUTHORIZED，不声称已发送。完整查询中心及发送器后续实施。

导出入口创建 RECEIVED 任务，支持发起人/类型/Idempotency-Key 范围内幂等；任务 ID 为字符串。当前不返回儿童资料、下载 URL 或 DONE，不将受理冒充身份验证。`dueAt`、`verifiedAt`、`resultRef` 等留给经过审批的后续办理流程。

成功审计随业务提交；Controller 异常在事务回滚后由独立事务保存失败记录。异常日志只记录异常类别、错误码和 requestId，不记录请求正文、儿童字段值或 SQL 参数。失败审计库不可用时写安全告警，需接入运维告警，不能保证故障期间数据库审计完整。

## 配置与发布

默认关闭 Q-01 数据处理；生产必须显式提供密钥和正式协议、目录及审批引用。dev/test 配置只允许合成演示，不代表生产签核。

`ProductionConfigGuard` 拒绝 prod 与 dev/test 混用、已知演示密钥和非30分钟 TTL；启用采集时还校验正式配置占位是否齐备。关闭闸门后，已有会话也不能选择角色、新建家庭或申请绑定；撤回、查询和权利申请继续可用。

生产不使用 MockWechatClient；微信客户端有连接/读取超时，错误返回安全业务码，不在异常日志中输出 secret/code。默认无 profile 不会隐式启用 mock，启动须明确选择 dev/test/prod。

初始化与增量脚本分离；唯一身份键不包含 delete_at。存量重复身份需人工审查，旧同意保持证据缺失状态，禁止自动提升为 VERIFIED。迁移风险和回滚限制见 `../sql/migrations/README.md`。

## 未完成的发布条件

- Q-01 前置账号处理依据、最低监护人核验强度和正式协议，Q-07 账号策略仍待审批。
- 正式年级/过敏原目录、保留期限、权利办理人员/工作日历、订阅授权和投递、频控配置待发布。
- 本期没有前端五 Tab，不包含钱包、菜单、删除执行或实际导出文件生成。
- 集成测试不是生产压测、实机验收或存量数据迁移演练，验证记录见 `sprint1-verification.md`。
