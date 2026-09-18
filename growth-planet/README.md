# 成长星球 V0.0.1 · Sprint 1（地基可运行）后端

技术栈：Spring Boot 4.1.1（Spring Framework 7 / Jakarta EE 11） · Java 21 · MyBatis-Plus 3.5.17（spring-boot4 starter）· JWT(jjwt 0.12.7) · MySQL · Redis。

> ⚠️ 本机 Maven 默认走阿里云镜像，尚未同步 Spring Boot 4.1.1。请**务必**使用工程内 `mvn-settings.xml`（单一 mirror 指向 Maven Central），并指定 JDK 21。

## 1. 构建与测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
MVN="JAVA_HOME=$JAVA_HOME /opt/homebrew/bin/mvn -s /Users/zhouwei/my-workspace/umbrella/growth-planet/mvn-settings.xml -U"

# 编译（验证 SB4 / Jakarta 包名 / Lombok / MyBatis-Plus starter 可解析）
$MVN clean compile

# 默认单元测试（纯逻辑，无需 Docker / MySQL / Redis，必须全绿）
$MVN test

# 集成测试（需 Docker；Testcontainers 拉起 MySQL + Redis）
$MVN test -Pintegration
```

默认 `mvn test` 仅运行 `JwtUtilTest` / `InviteCodeUtilTest`（纯逻辑）。逻辑删除与三组接口的端到端测试（`LogicDeleteTest` / `AuthFlowIT` / `FamilyFlowIT` / `ComplianceFlowIT`）位于 `integration` profile，需 Docker。

## 2. 本地起服务

1. 准备 MySQL（8.0+）与 Redis（7+），建库 `growth_planet`，执行 `sql/sprint1_schema.sql` 建表。
2. 按需修改 `src/main/resources/application-dev.yml` 的数据源 / Redis 连接。
3. 启动：`$MVN spring-boot:run`（或 `java -jar target/growth-planet.jar`）。
4. 微信登录在 dev/test 使用 `MockWechatClient`：`POST /api/auth/wx-login {"code":"任意字符串"}` → 返回 token（openid 由 code 派生，便于多用户测试）。

## 3. 接口一览（统一前缀 /api，统一响应 {code,data,message}）

| 组 | 方法 | 路径 | 角色 |
|----|------|------|------|
| AUTH | POST | /api/auth/wx-login | 公开 |
| AUTH | POST | /api/auth/select-role | 登录后 |
| AUTH | POST | /api/child/profile | CHILD |
| FAMILY | POST | /api/family/create | PARENT |
| FAMILY | GET  | /api/family/invite-code | PARENT |
| FAMILY | POST | /api/family/join | CHILD |
| FAMILY | POST | /api/family/bind-approve | PARENT |
| COMPLIANCE | GET  | /api/compliance/consent | PARENT |
| COMPLIANCE | POST | /api/compliance/consent | PARENT |
| COMPLIANCE | POST | /api/compliance/consent/revoke | PARENT |
| COMPLIANCE | POST | /api/compliance/data-export | PARENT |

除 wx-login 外，所有接口需 `Authorization: Bearer <token>`。错误码 E-001~E-010（详见 `common/result/ResultCode`）。

## 4. 关键设计说明

- **逻辑删除**：`delete_at BIGINT DEFAULT 0`；查询自动追加 `delete_at = 0`；删除由业务用 `UpdateWrapper.setSql("delete_at = UNIX_TIMESTAMP() * 1000")` 注入时间戳（避免 MyBatis-Plus 对函数取值加引号），实现「删了还能重建」（复合唯一键 `UNIQUE(col, delete_at)`）。Sprint 1 不执行物理删除。
- **三角色分权**：`JwtInterceptor` + ThreadLocal `UserContext`，`@RequireRole` 方法级校验；角色不符或跨家庭访问 → E-009(403)。
- **数据隔离**：Service 层查询强制 `.eq("family_id", ctx.familyId())`，儿童接口额外 `.eq("user_id", ctx.userId())`；跨家庭记录取出后二次比对 `family_id` → E-009。
- **撤回即时降级**：`consent/revoke` 将当前 token 的 `jti` 写入 Redis 黑名单（`jwt:blacklist:{jti}`，TTL=token 剩余有效期）；拦截器命中黑名单 → E-001(401)。Redis 不可用时拦截器 fail-open 放行。
- **审计**：LOGIN/ROLE/CREATE_FAMILY/JOIN/BIND/GRANT/REVOKE/EXPORT 等写操作点调用 `AuditService.record` 写入 `sys_audit_log`。
