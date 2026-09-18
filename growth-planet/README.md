# 成长星球 V0.0.1 后端

以 `../docs/成长星球_V0.0.1_审计修订基线.md` 为需求基线。2026-09-18 在 Sprint 1 收口基础上实现钱包、菜单、确认审批、通知中心及隐私办理基础；基础包为 `cn.studykid.growthplanet`。后端交付不代表各 Sprint 整体退出或真实儿童数据上线验收通过。

## 构建与测试

在 `growth-planet` 目录执行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
/opt/homebrew/bin/mvn -s mvn-settings.xml test
/opt/homebrew/bin/mvn -s mvn-settings.xml test -Pintegration
```

默认测试无需数据库。`integration` 使用 Testcontainers 隔离 MySQL 8.0.36 和 Redis 7.2，按序加载 Sprint 1–4 的5个 SQL；迁移专用测试独立从 Sprint 1 开始。不连接本地业务库；首次运行需下载容器镜像。已有 Maven 依赖可加 `-o` 离线构建。

本机 Colima / Docker 29 的测试进程参数：

```bash
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
/opt/homebrew/bin/mvn -o -s mvn-settings.xml -Dapi.version=1.44 test -Pintegration
```

这些参数只影响当前进程，不需要修改 Docker 全局配置或升级依赖。

## 本地运行

1. 仅用合成数据，准备独立空库和 Redis；人工审查后依次执行 `sql/sprint1_schema.sql`、`sql/sprint2_schema.sql`、`sql/sprint3_catalog.sql`、`sql/sprint3_confirmation.sql`、`sql/sprint4_schema.sql`。
2. 存量库禁止执行初始化脚本，先阅读 [Sprint 1迁移说明](sql/migrations/README.md)和[Sprint 2–4迁移说明](docs/sprint2-4-migration.md)。本次未操作现有数据库。
3. 配置 `application-dev.yml` 中的数据库与 Redis 地址，明确启用 `dev`：

```bash
/opt/homebrew/bin/mvn -s mvn-settings.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev/test` 的 mock code 为1至51字符，仅作合成身份。生产须显式使用 `prod`，提供独立的 `JWT_SECRET`、`WX_APPID`、`WX_SECRET`。默认不开放数据处理；Q-01 审批、正式协议、核验强度、年级与过敏原目录发布后才可配置 `compliance`。禁止把合成目录或演示密钥用于生产。

## 接口

统一前缀 `/api`；除 `auth/wx-login` 外均需 Bearer token。

| 方法 | 路径 | 角色 / 行为 |
| --- | --- | --- |
| POST | /auth/wx-login | 公开；Q-01 闸门通过后最小登录，expiresIn=1800 |
| POST | /auth/select-role | 一次选择 CHILD/PARENT；同角色幂等，禁止切换 |
| POST | /auth/logout | 登录用户；使该账号全部旧会话失效 |
| POST | /family/create | PARENT；本期一个家庭 |
| GET | /family/invite-code | PARENT；有效绑定家庭 |
| POST | /family/join | CHILD；最小申请，拒绝后在原关系重申 |
| GET | /family/children | PARENT；本家庭儿童申请/关系分页，含 PENDING/BOUND/REJECTED |
| GET | /family/binding | CHILD；本人有效关系，或最近拒绝申请；无申请为 NONE |
| POST | /compliance/consent | PARENT；指定 childId/applyId/PROFILE/v1，agreed=true、年龄18至120 |
| GET | /compliance/consent | PARENT；必传 childId、consentType |
| POST | /family/bind-approve | PARENT；有效 PROFILE 同意后才能通过 |
| POST | /child/profile | PARENT；BOUND 且同意有效，完整字段校验 |
| GET | /child/profile | PARENT；childId 必填，BOUND 且同意有效；未建档404 |
| GET | /child/preferences | 本人 CHILD / 所属 PARENT；childId 必填，BOUND、有效同意及 COMPLETE 档案 |
| PUT | /child/preferences | CHILD 本人；仅 dislikes/tastes，拒绝所有额外字段 |
| POST | /compliance/consent/revoke | PARENT；childId、consentType、version；成功200 |
| POST | /compliance/data-export | PARENT；已绑定儿童；Idempotency-Key 必填 |
| GET | /compliance/requests/{id} | 发起家长或显式获授权隐私管理员；审计查询 |

Sprint 2–4 的钱包及审批契约见[接口补充](docs/sprint2-4-api.md)，菜单接口见[菜单模块说明](docs/sprint3-catalog.md)，通知和隐私接口见[通知与隐私说明](docs/sprint4-sidecar-handoff.md)。

错误 HTTP：参数400、未登录401、越权403、不可见404、方法405、状态/版本409、下载过期410、内容类型415、系统500、依赖不可用503。统一响应 `{code,data,message,requestId}`；成功码为数字0，错误码为 `"E-xxx"`。业务 ID 为十进制字符串，金额为两位小数字符串，时间戳/秒数为数字，整数参数不接受小数截断。

## 调用示例

顺序：登录 → 选角色 → 家长建家庭 → 儿童申请 → 家长查询申请 → 家长同意 → 家长审批 → 家长建档 → 儿童维护非安全偏好。

家长从 `GET /api/family/children?bindStatus=PENDING&page=1&pageSize=20` 获得同意与审批所需的 childId/applyId，无需从数据库或儿童端手工获取。省略 bindStatus 返回所有儿童关系，按 applyId 升序；page≥1，pageSize 为1至100，默认20；空页返回 items=[]，total 为过滤后的总数。

```json
{"code":0,"data":{"items":[{"familyId":"8","childId":"12","applyId":"31","bindStatus":"PENDING","applicationVersion":1,"relationLabel":null}],"total":1,"page":1,"pageSize":20},"message":"success","requestId":"example-family-query"}
```

儿童通过 `GET /api/family/binding` 恢复本人状态，不要求 token 已含 familyId；优先返回 PENDING/BOUND，否则返回最近更新的 REJECTED。无申请时：

```json
{"code":0,"data":{"familyId":null,"childId":"12","applyId":null,"bindStatus":"NONE","applicationVersion":null,"relationLabel":null},"message":"success","requestId":"example-binding-query"}
```

存在关系时 data 与上述 items 单项结构相同。关系查询不返回昵称、学校、过敏或邀请码，撤回后仍可查最小关系状态；档案/偏好查询则必须重新校验有效同意。

`POST /api/auth/wx-login`：

```json
{"code":"synthetic_parent_1"}
```

```json
{"code":0,"data":{"token":"example-token","openid":"mock_openid_synthetic_parent_1","role":"UNSELECTED","isNew":true,"expiresIn":1800},"message":"success","requestId":"example-request-login"}
```

`POST /api/compliance/consent`：

```json
{"childId":"12","applyId":"31","consentType":"PROFILE","version":"v1","selfReportedAge":30,"agreed":true}
```

```json
{"code":0,"data":{"agreementText":null,"version":"v1","currentStatus":"GRANTED","guardianStatus":"SELF_ATTESTED"},"message":"success","requestId":"example-request-1"}
```

`POST /api/child/profile`，以下年级和过敏原仅对应演示目录：

```json
{"childId":"12","nickname":"测试儿童","grade":"三年级","school":"合成学校","allergies":["PEANUT"],"dislikes":[],"tastes":["清淡"]}
```

```json
{"code":0,"data":{"profileStatus":"COMPLETE"},"message":"success","requestId":"example-request-2"}
```

家长回显 `GET /api/child/profile?childId=12`：

```json
{"code":0,"data":{"childId":"12","nickname":"测试儿童","grade":"三年级","school":"合成学校","allergies":["PEANUT"],"dislikes":[],"tastes":["清淡"],"profileStatus":"COMPLETE"},"message":"success","requestId":"example-profile-query"}
```

儿童 `PUT /api/child/preferences`，身份由服务端确定，不传 childId：

```json
{"dislikes":["芹菜"],"tastes":["清淡"]}
```

```json
{"code":0,"data":{"childId":"12","dislikes":["芹菜"],"tastes":["清淡"],"favoriteDishIds":[]},"message":"success","requestId":"example-preferences"}
```

`GET /api/child/preferences?childId=12` 返回相同 data；允许本人儿童或所属家长查询，不暴露安全字段。两个数组均必填，各最多20项，元素非空且最多64字符；空数组清空对应偏好。注入 allergies、childId、familyId、role 或任意额外字段均400/E-400，不做静默忽略。家长需继续通过完整档案接口修改安全字段。

未绑定403；未建档/档案不完整时偏好接口409/E-002；同意失效或撤回后档案/偏好读写409/E-010；采集闸门关闭403。儿童使用绑定家庭创建家长的有效同意，家长停用或关系无效时儿童不可操作。Sprint 3 新增持久化 `favoriteDishIds`，通过 `POST /api/menu/mark-favorite` 增删，不能通过偏好 PUT 注入收藏或安全字段；需先执行收藏字段迁移。

`POST /api/compliance/data-export`，请求头 `Idempotency-Key: export_12_1`：

```json
{"childId":"12"}
```

```json
{"code":0,"data":{"taskId":"80","status":"RECEIVED","requestType":"EXPORT","dueAt":null,"errorCode":null,"expiresAt":null,"version":0,"downloadAvailable":false},"message":"success","requestId":"example-request-3"}
```

这是持久化申请，不是导出文件。受理时 `dueAt=null`；获授权隐私管理员转为 PROCESSING 时必须填写未来期限和凭证编号。工作日历、人员和正式时限仍待批准。同键同参返回原任务，同键不同参返回409/E-012；客户端超时必须同键重试。READY 后提供24小时鉴权下载，但目前仅生成当前档案/偏好及最近至多500条本人同意元数据，不是全量历史数据包。

`POST /api/compliance/consent/revoke`：

```json
{"childId":"12","consentType":"PROFILE","version":"v1"}
```

```json
{"code":0,"data":{"status":"REVOKED"},"message":"success","requestId":"example-request-revoke"}
```

撤回本身成功返回200；随后写档案返回409：

```json
{"code":"E-010","data":null,"message":"同意缺失、失效或已撤回","requestId":"example-request-denied"}
```

## 边界

历史证据见 [Sprint 1验证](docs/sprint1-verification.md)，本轮见 [Sprint 2–4验证](docs/sprint2-4-verification.md)。本仓库只有后端，五 Tab、页面交互与真实微信授权未交付。真实订阅发送适配器尚未实现且默认关闭；重试框架不能替代平台联调。全量导出、实际删除和恢复演练、性能与实机验收仍待完成。Q-01/Q-06/Q-07 和数据保留策略等审批未关闭，禁止将合成测试结果视为上线批准。
