# Sprint 2–4 后端接口补充

日期：2026-09-18。仅记录已实现接口，不代表前端或平台验收。统一 Bearer 会话、家庭权限及当前同意校验；金额为两位小数字符串，业务 ID 为十进制字符串。

## 钱包

| 方法及路径 | 参数及行为 |
| --- | --- |
| GET `/api/wallet/balance` | childId 必填；本人 CHILD 或所属 PARENT；初始余额0 |
| POST `/api/wallet/grant` | PARENT；childId/amount/reason，Idempotency-Key 必填；0.01–9999.99，两位小数 |
| GET `/api/wallet/allowance-rule` | childId 必填；默认单笔30、日30、周150 |
| PUT `/api/wallet/allowance-rule` | PARENT；childId/singleLimit/dailyLimit/weeklyLimit/expectedVersion |
| GET `/api/wallet/allowance-log` | childId 必填；startDate/endDate 可选，最多31天；page≥1、pageSize≤100 |

发放和提报幂等键均为 `[A-Za-z0-9_-]{1,64}`；同键同参返回原操作结果快照，同键不同参409/E-012。发放键按操作家长隔离，提报键按儿童隔离。余额与累计不能由客户端指定。

`POST /api/wallet/grant`，请求头 `Idempotency-Key: grant_001`：

```json
{"childId":"12","amount":"50.00","reason":"本周练习额度"}
```

```json
{"code":0,"data":{"childId":"12","balance":"50.00","version":1,"logId":"7"},"message":"success","requestId":"example-grant"}
```

更新额度：

```json
{"childId":"12","singleLimit":"30.00","dailyLimit":"30.00","weeklyLimit":"150.00","expectedVersion":0}
```

返回 data 示例：

```json
{"childId":"12","singleLimit":"30.00","dailyLimit":"30.00","weeklyLimit":"150.00","dailyUsed":"0.00","weeklyUsed":"0.00","dailyPeriod":"2026-09-18","weeklyPeriod":"2026-09-14","version":1}
```

规则必须满足 `0≤单笔≤日≤周≤9999.99`；版本不符409/E-007。Asia/Shanghai 的每日及周一边界独立结算，累计只计成功扣款；发放不计消费额。余额采用 DECIMAL(10,2)，最高99999999.99；累积消费采用 DECIMAL(14,2)，避免显式超额及反复充值后累计范围过小。

## 确认与审批

| 方法及路径 | 参数及行为 |
| --- | --- |
| POST `/api/menu/confirm` | CHILD；menuId/items/remark/previousConfirmId；Idempotency-Key 必填 |
| GET `/api/menu/confirm/{id}` | 本人儿童或所属家长；不可见资源404 |
| GET `/api/menu/confirm/status` | CHILD 本人；confirmId；仅 confirmId/status/version |
| GET `/api/menu/confirms` | childId 必填，status 可选，分页 |
| GET `/api/parent/approvals` | PARENT；childId 必填；status 默认 PENDING；分页 |
| POST `/api/menu/confirm/{id}/withdraw` | CHILD；expectedVersion；PENDING→CANCELLED，重复撤回返回原状态 |
| POST `/api/parent/approve/{id}/approve` | PARENT；expectedVersion，超额时附显式确认预览字段 |
| POST `/api/parent/approve/{id}/reject` | PARENT；expectedVersion/reason；PENDING→REJECTED |
| POST `/api/parent/approve/{id}/modify` | PARENT；expectedVersion/reason/items；保存建议，不修改原明细，状态REJECTED |

提报只支持当日已发布 FAMILY 菜单，1–20种菜且无重复，数量1–9，备注≤255字符；必须 COMPLETE 档案、有效同意、菜品在售且过敏原已声明并安全。SCHOOL 只展示和收藏。前端提交总额等额外字段400；服务端取价并保存不可变快照。

`POST /api/menu/confirm`，请求头 `Idempotency-Key: submit_001`：

```json
{"menuId":"20","items":[{"dishId":"6","quantity":1,"note":"少盐"}],"remark":"午餐"}
```

返回 data 示例，提交不扣款：

```json
{"confirmId":"30","childId":"12","menuId":"20","previousConfirmId":null,"status":"PENDING","version":0,"menuDate":"2026-09-18","mealType":"LUNCH","isOverLimit":false,"totalAmount":"18.00","estBalanceAfter":"32.00","balance":null,"walletVersion":null,"items":[{"dishId":"6","dishName":"合成测试餐","quantity":1,"unitPrice":"18.00","subtotal":"18.00","note":"少盐"}],"childVisibleNote":"等待家长确认","suggestedItems":[]}
```

普通批准：

```json
{"expectedVersion":0}
```

成功后 data.status=COMPLETED、version=1、balance=32.00、walletVersion=2。余额、额度累计、唯一流水、状态、审批记录、双通道事件和审计在同一事务提交；任何一步失败全部回滚。批准重新校验日期、菜品与过敏，仍按提交时价格结算。

超额返回 HTTP409/E-011 示例：

```json
{"code":"E-011","data":{"confirmId":"31","confirmVersion":0,"walletVersion":2,"ruleVersion":1,"usageDate":"2026-09-18","totalAmount":"20.00","balance":"32.00","singleLimit":"30.00","dailyUsed":"18.00","dailyLimit":"30.00","weeklyUsed":"18.00","weeklyLimit":"150.00","requiresExplicitConfirm":true},"message":"额度已变化，请重新确认","requestId":"example-preview"}
```

家长重新确认后：

```json
{"expectedVersion":0,"explicitConfirm":true,"walletVersion":2,"ruleVersion":1,"confirmVersion":0,"usageDate":"2026-09-18"}
```

超额时必须匹配最新钱包、规则、确认单版本和使用日期；布尔值不能单独放行。预览失效再次409/E-011；余额不足409/E-006。COMPLETED 重试必须是同一批准家长、相同命令字段，返回原结算结果；变更命令409/E-007。拒绝/撤回后用新幂等键及 previousConfirmId 新建单。家长填写的 reason 是面向儿童的温和说明，在拒绝/修改后的详情中通过 childVisibleNote 回读；客户端不得将其当作仅家长可见的内部备注，状态轮询仍只返回 ID、状态和版本。

审批捕获一次 Asia/Shanghai 业务日期，菜单校验、额度周期、超额预览和扣款流水共用该日期；实际扣款前发现跨日（含跨周）返回409/E-007并回滚，不把旧菜单移入次日消费。

拒绝请求 `POST /api/parent/approve/30/reject`：

```json
{"expectedVersion":0,"reason":"今天先选便宜点的好不好？"}
```

返回 data 示例（儿童与所属家长的详情均可回读说明）：

```json
{"confirmId":"30","childId":"12","menuId":"20","previousConfirmId":null,"status":"REJECTED","version":1,"menuDate":"2026-09-18","mealType":"LUNCH","isOverLimit":false,"totalAmount":"18.00","estBalanceAfter":"32.00","balance":null,"walletVersion":null,"items":[{"dishId":"6","dishName":"合成测试餐","quantity":1,"unitPrice":"18.00","subtotal":"18.00","note":"少盐"}],"childVisibleNote":"今天先选便宜点的好不好？","suggestedItems":[]}
```

## 通知与隐私

详见[通知与隐私接口及边界](sprint4-sidecar-handoff.md)。通知 IN_APP 查询和未读数不依赖订阅授权；逐事件授权仅是本人声明，不等价微信平台授权证明。真实发送适配器未实现、默认禁发。

儿童发现今日没有家庭餐单时，可调用 `POST /api/mini/child/menu-reminder` 提醒当前家庭创建家长。请求体为空对象：

```json
{}
```

仅允许当前登录的 `CHILD` 角色，服务端从会话解析儿童和家庭，不接受客户端传入的家庭 ID、家长 ID 或日期；儿童必须在该家庭中处于有效 `BOUND` 关系。事件按儿童和 Asia/Shanghai 业务日期幂等，同一儿童当天重复请求不会新增通知。

首次创建返回：

```json
{"code":0,"data":{"status":"CREATED"},"message":"success","requestId":"example-reminder"}
```

当天已有提醒时返回：

```json
{"code":0,"data":{"status":"ALREADY_EXISTS"},"message":"success","requestId":"example-reminder-repeat"}
```

服务端为家庭创建家长写入一条 `IN_APP` 站内通知，并同步建立 `SUBSCRIBE` 记录。`SUBSCRIBE` 初始状态为 `UNAUTHORIZED`，仅表示通知事件已登记，不代表已获得微信授权或已完成平台送达；真实微信订阅授权、发送和真机端到端联调仍需单独验收。该接口不新增字段、索引或迁移。

隐私请求幂等键为 `[A-Za-z0-9_-]{1,128}` 且必填；DELETE 须新微信 code 重新验证账号身份，不能据此声称完成监护关系核验。管理员须 ADMIN 加 `privacy.operator-ids` 显式配置。工单流转是人工办理记录，COMPLETED 不会触发自动数据删除；READY 仅开放有限范围的24小时鉴权下载，过期410/E-410。撤回同意不会自动剥夺权利申请入口。

## 升级注意

新增字段、唯一键和执行顺序见[迁移说明](sprint2-4-migration.md)。本轮没有对任何现有业务库执行 DDL。现有调用方须升级必填幂等键，并允许偏好响应新增 favoriteDishIds、隐私请求响应新增 requestType/expiresAt/version。接口示例是合成数据，不含真实儿童信息。
