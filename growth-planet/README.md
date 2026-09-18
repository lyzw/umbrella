# 成长星球 Sprint 1 后端

以 `../docs/成长星球_V0.0.1_审计修订基线.md` 为需求基线。2026-09-18 修复旧实现中的身份、授权、绑定、撤回、日志和接口契约问题；不代表真实儿童数据上线验收通过。

## 构建与测试

在 `growth-planet` 目录执行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
/opt/homebrew/bin/mvn -s mvn-settings.xml test
/opt/homebrew/bin/mvn -s mvn-settings.xml test -Pintegration
```

默认测试无需数据库。`integration` 使用 Testcontainers 隔离 MySQL 8.0.36 和 Redis 7.2，直接复用 `sql/sprint1_schema.sql`，不连接本地业务库；首次运行需下载容器镜像。已有 Maven 依赖可加 `-o` 离线构建。

本机 Colima / Docker 29 的测试进程参数：

```bash
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
/opt/homebrew/bin/mvn -o -s mvn-settings.xml -Dapi.version=1.44 test -Pintegration
```

这些参数只影响当前进程，不需要修改 Docker 全局配置或升级依赖。

## 本地运行

1. 仅用合成数据，准备独立空库和 Redis；人工审查后执行 `sql/sprint1_schema.sql`。
2. 存量库禁止执行初始化脚本，先阅读 `sql/migrations/README.md`。本次未操作现有数据库。
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
| POST | /compliance/consent | PARENT；指定 childId/applyId/PROFILE/v1，agreed=true、年龄18至120 |
| GET | /compliance/consent | PARENT；必传 childId、consentType |
| POST | /family/bind-approve | PARENT；有效 PROFILE 同意后才能通过 |
| POST | /child/profile | PARENT；BOUND 且同意有效，完整字段校验 |
| POST | /compliance/consent/revoke | PARENT；childId、consentType、version；成功200 |
| POST | /compliance/data-export | PARENT；已绑定儿童；可带 Idempotency-Key |
| GET | /compliance/requests/{id} | 仅发起家长；其他账号404 |

错误 HTTP：参数400、未登录401、越权403、不可见404、方法405、状态/版本409、内容类型415、系统500、依赖不可用503。统一响应 `{code,data,message,requestId}`；成功码为数字0，错误码为 `"E-xxx"`。业务 ID 为十进制字符串，时间戳/秒数为数字，整数参数不接受小数截断。

## 调用示例

顺序：登录 → 选角色 → 家长建家庭 → 儿童申请 → 家长同意 → 家长审批 → 家长建档。

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

`POST /api/compliance/data-export`，请求头 `Idempotency-Key: export_12_1`：

```json
{"childId":"12"}
```

```json
{"code":0,"data":{"taskId":"80","status":"RECEIVED","dueAt":null,"errorCode":null,"downloadAvailable":false},"message":"success","requestId":"example-request-3"}
```

这是持久化申请，不是导出文件。工作日历、办理人员、身份复核及期限由后续权利流程确认，当前 `dueAt=null`，不虚构完成时间。同键同参返回原任务，同键不同参返回409/E-012；客户端超时必须同键重试。

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

详见 `docs/sprint1-architecture.md` 和 `docs/sprint1-verification.md`。本仓库只有后端，五 Tab 不在本次后端修复范围。钱包/菜单、完整通知中心、订阅实际投递、导出下载/删除办理、性能和实机验收仍按后续 Sprint 推进。Q-01/Q-07 和数据保留策略等审批仍未关闭。
