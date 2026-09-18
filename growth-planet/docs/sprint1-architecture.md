# 成长星球 V0.0.1 · Sprint 1（地基可运行）架构设计与任务分解

> 文档版本：V1.0 ｜ 作者：架构师（高见远）｜ 日期：2026-09-17
> 上游：详细分析设计文档（第 4/5.1/5.3/6.1/6.2/6.8/7.3/8 章、E-001~E-010、Q-01~Q-06、10.1）、开发计划（Sprint 1 章节）
> 适用范围：仅 Sprint 1（6 表 + AUTH/FAMILY/COMPLIANCE 三组接口 + 三角色 JWT 分权 + 合规审计）。不含前端 5-Tab UI，后端只提供 API。

---

## 0. 决策摘要（结论先行）

**技术栈最终锁定（已按新口径落地，均经网络核验可解析）：**

| 组件 | 版本 / 坐标 | 说明 / 依据 |
|------|-------------|-------------|
| JDK | **21.0.10**（本机 `/Library/Java/JavaVirtualMachines/jdk-21.jdk`） | `<java.version>21</java.version>` |
| Spring Boot | **4.1.1** | Maven Central 已发布（2026-08-20），基于 Spring Framework 7.0.x / Jakarta EE 11 |
| MyBatis-Plus | **3.5.17**（专用 starter `com.baomidou:mybatis-plus-spring-boot4-starter`） | 3.5.15+ 官方兼容 SB4 / Spring Framework 7；3.5.17 为当前稳定版 |
| JWT | **io.jsonwebtoken:jjwt 0.12.7**（api + impl + jackson） | 0.12.x 当前稳定，支持 JDK 17+ / Jakarta |
| MySQL Connector/J | `com.mysql:mysql-connector-j`（由 SB4 BOM 管理，解析到 9.x） | 新坐标（自 8.0.33 起），支持 MySQL 8.0+ 与 JDK 21 |
| Redis 客户端 | `spring-boot-starter-data-redis`（Lettuce，由 SB4 BOM 管理） | Sprint 1 启用，用于 JWT 黑名单（防重放/撤回降级）+ 预留 refresh |
| 构建 | **Maven 3.9.14**（`/opt/homebrew/bin/mvn`），`spring-boot-starter-parent` 继承 | — |

**核心裁定沿用（不可推翻）：** `delete_at BIGINT DEFAULT 0` 逻辑删除；业务唯一键 `UNIQUE(biz_col, delete_at)` 实现"删了还能重建"；扣减统一在家长同意后（Sprint 1 无扣减，仅预留）；三角色 JWT 分权（CHILD/PARENT/ADMIN）；统一响应体 `{code,data,message}`；错误码复用 E-001~E-010；金额 DECIMAL(10,2) 标注虚拟；所有业务查询按 `family_id` 过滤、儿童仅访问本人记录。

**IS_PASS = ✅ PASS（设计自洽，可直接编码）。** 唯一需构建期实测点见 §9 待明确事项（均为低风险、可回落项）。

---

## 1. 实现方案 + 框架选型

### 1.1 技术难点与选型理由
1. **Spring Boot 4 适配**：SB4 基于 Spring Framework 7.0 / Jakarta EE 11 / Spring Security 7.1。MyBatis-Plus 必须使用专用 `mybatis-plus-spring-boot4-starter`（3.5.13 起提供，3.5.15+ 正式兼容；用错 starter 会导致 `@Mapper` 无法注册，报 `NoSuchBeanDefinitionException`）。**这是 SB4 最关键的一个坑，必须在 pom 中写对坐标。**
2. **逻辑删除 BIGINT 时间戳**：默认 `@TableLogic` 假设布尔型，需改为"未删除=0，删除=`UNIX_TIMESTAMP()*1000`"的 SQL 函数。方案见 §1.3。
3. **三角色分权**：用自定义 `HandlerInterceptor` + ThreadLocal 上下文，不引入 Spring Security（保持轻量、可控）。token 含 `user_id/role/family_ids[]`，拦截器注入上下文并对 `@RequireRole` 校验。
4. **微信登录 mock 化**：定义 `WechatClient` 接口，生产用 `WxWechatClient`（调 code2Session），开发/测试用 `MockWechatClient`（按 profile 切换），使 Sprint 1 无需真实 appid 即可全链路跑通。
5. **数据隔离**：逻辑删除自动加 `delete_at=0`；`family_id` 由拦截器从 JWT 取出注入到 `UserContext`，Service 层所有业务查询必须 `.eq("family_id", ctx.familyId())`；儿童接口额外 `.eq("user_id", ctx.userId())`。

### 1.2 架构模式
- **分层**：controller（REST 入口）→ service/impl（业务逻辑 + 事务）→ mapper（MyBatis-Plus）→ entity（DO）。
- **横切**：config（MyBatis-Plus/JWT/Redis/WebMvc）、common（统一响应/错误码/异常/上下文/枚举/注解）、util（JwtUtil/InviteCodeUtil/JsonUtils）。
- **包基础名**：`com.growthplanet`（与产品名一致，避免与未来模块冲突）。

### 1.3 逻辑删除落地方案（BIGINT 时间戳）
在 `application.yml` 全局配置（单一数据源，推荐）：
```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleteAt      # 所有实体中名为 deleteAt 的字段自动逻辑删除
      logic-not-delete-value: 0         # 未删除
      logic-delete-value: UNIX_TIMESTAMP() * 1000   # 删除时置为毫秒时间戳（MySQL 函数，MyBatis-Plus 以 ${} 原样写入，不引号）
```
- 实体字段 `private Long deleteAt = 0L;`（Java 侧默认 0，与 DDL `DEFAULT 0` 一致；逻辑删除字段不参与 `MetaObjectHandler` 填充）。
- 查询/连表自动追加 `WHERE delete_at = 0`；`mapper.deleteById` 自动转为 `UPDATE ... SET delete_at = UNIX_TIMESTAMP()*1000 WHERE ... AND delete_at = 0`。
- **兜底方案（若实测发现运行时给函数加引号）**：改为逐字段注解 `@TableLogic(value = "0", delval = "UNIX_TIMESTAMP() * 1000")`，效果相同。两方案二选一，优先全局配置。

### 1.4 Redis 在 Sprint 1 的用途（已拍板：启用）
- **启用** `RedisTemplate<String,String>`。
- **用途 1（核心）**：JWT 黑名单 `jwt:blacklist:{jti}`（TTL=token 剩余有效期）。当监护人撤回同意（E-010）或管理员禁用账号时，将当前 token 的 `jti` 拉黑；拦截器在放行前查黑名单，命中则返回 401（E-001）。这给"撤回即时降级"提供物理强制力。
- **用途 2（预留）**：refresh token 存储（Sprint 1 仅预留 `refresh` 端点骨架，不强求）。
- **降级策略**：Redis 不可用时拦截器按"未拉黑"放行（fail-open），保证"全链路可跑通"不被 Redis 阻断；这是安全/可用性的权衡，见 §9。

---

## 2. 文件清单（Sprint 1 全部待建文件）

工程根：`/Users/zhouwei/my-workspace/umbrella/growth-planet/`

| 路径 | 职责 |
|------|------|
| `pom.xml` | Maven 依赖（见 §7），`spring-boot-starter-parent` + `java.version=21` |
| `sql/sprint1_schema.sql` | 6 张表 DDL（含 `delete_at`、复合唯一键、索引） |
| `src/main/java/com/growthplanet/GrowthPlanetApplication.java` | 启动类（`@SpringBootApplication` + `@MapperScan("com.growthplanet.mapper")`） |
| `src/main/java/com/growthplanet/config/MybatisPlusConfig.java` | `MetaObjectHandler` 自动填充 create_time/update_time；分页插件（预留） |
| `src/main/java/com/growthplanet/config/WebMvcConfig.java` | 注册 `JwtInterceptor`，exclude 公开接口与 actuator/health |
| `src/main/java/com/growthplanet/config/JwtConfig.java` | `JwtProperties`（secret、accessTtl、issuer）绑定 |
| `src/main/java/com/growthplanet/config/RedisConfig.java` | `RedisTemplate<String,String>` Bean + 序列化 |
| `src/main/java/com/growthplanet/common/result/Result.java` | 统一响应体 `{code,data,message}` + 静态工厂 `ok()/fail()` |
| `src/main/java/com/growthplanet/common/result/ResultCode.java` | 错误码枚举 E-001~E-010（code 数值/HTTP 状态/文案） |
| `src/main/java/com/growthplanet/common/exception/BizException.java` | 业务异常（携带 `ResultCode`） |
| `src/main/java/com/growthplanet/common/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` 统一捕获 → `Result` |
| `src/main/java/com/growthplanet/common/context/LoginUser.java` | 登录用户值对象（userId/role/familyIds/jti） |
| `src/main/java/com/growthplanet/common/context/UserContext.java` | ThreadLocal 持有/清除 `LoginUser` |
| `src/main/java/com/growthplanet/common/enums/RoleEnum.java` | CHILD / PARENT / ADMIN / UNSET |
| `src/main/java/com/growthplanet/common/enums/BindStatusEnum.java` | PENDING / APPROVED / REJECTED |
| `src/main/java/com/growthplanet/common/enums/GuardianStatusEnum.java` | NOT_REQUIRED / PENDING / APPROVED / REVOKED |
| `src/main/java/com/growthplanet/common/enums/ProfileStatusEnum.java` | INCOMPLETE / COMPLETED |
| `src/main/java/com/growthplanet/common/enums/ConsentActionEnum.java` | GRANT / REVOKE / EXPORT（写 usr_consent_log / sys_audit_log 的 action） |
| `src/main/java/com/growthplanet/common/annotation/RequireRole.java` | 方法级角色校验注解 |
| `src/main/java/com/growthplanet/util/JwtUtil.java` | 签发/解析/校验 JWT（jjwt 0.12.x），claims: user_id/role/family_ids/jti/exp |
| `src/main/java/com/growthplanet/util/InviteCodeUtil.java` | 生成 6 位大写字母数字邀请码（带去重重试） |
| `src/main/java/com/growthplanet/util/JsonUtils.java` | Jackson 封装（JSON↔对象/List） |
| `src/main/java/com/growthplanet/entity/User.java` | usr_user |
| `src/main/java/com/growthplanet/entity/Family.java` | usr_family |
| `src/main/java/com/growthplanet/entity/FamilyMember.java` | usr_family_member |
| `src/main/java/com/growthplanet/entity/ChildProfile.java` | usr_child_profile（JSON 字段用 `JacksonTypeHandler`） |
| `src/main/java/com/growthplanet/entity/ConsentLog.java` | usr_consent_log |
| `src/main/java/com/growthplanet/entity/AuditLog.java` | sys_audit_log |
| `src/main/java/com/growthplanet/mapper/UserMapper.java` | extends `BaseMapper<User>` |
| `src/main/java/com/growthplanet/mapper/FamilyMapper.java` | extends `BaseMapper<Family>` |
| `src/main/java/com/growthplanet/mapper/FamilyMemberMapper.java` | extends `BaseMapper<FamilyMember>` |
| `src/main/java/com/growthplanet/mapper/ChildProfileMapper.java` | extends `BaseMapper<ChildProfile>` |
| `src/main/java/com/growthplanet/mapper/ConsentLogMapper.java` | extends `BaseMapper<ConsentLog>` |
| `src/main/java/com/growthplanet/mapper/AuditLogMapper.java` | extends `BaseMapper<AuditLog>` |
| `src/main/java/com/growthplanet/dto/request/`（9 个） | WxLoginReq / SelectRoleReq / ChildProfileReq / CreateFamilyReq / JoinFamilyReq / BindApproveReq / ConsentReq / RevokeConsentReq / DataExportReq |
| `src/main/java/com/growthplanet/dto/response/`（9 个） | 对应 *Resp（见 §4） |
| `src/main/java/com/growthplanet/service/WechatClient.java` | 接口：`code2Session(String code) → WxSession` |
| `src/main/java/com/growthplanet/service/impl/WxWechatClient.java` | 生产实现（RestTemplate 调微信） |
| `src/main/java/com/growthplanet/service/impl/MockWechatClient.java` | 开发/测试实现（返回固定 openid） |
| `src/main/java/com/growthplanet/service/AuthService.java` + `impl/AuthServiceImpl.java` | 微信登录 / 角色选择 / 儿童档案 |
| `src/main/java/com/growthplanet/service/FamilyService.java` + `impl/FamilyServiceImpl.java` | 家庭创建 / 邀请码 / 加入 / 绑定审批 |
| `src/main/java/com/growthplanet/service/ComplianceService.java` + `impl/ComplianceServiceImpl.java` | 同意书查询 / 提交 / 撤回 / 数据导出 |
| `src/main/java/com/growthplanet/service/AuditService.java` + `impl/AuditServiceImpl.java` | sys_audit_log 写入（被各 service 调用） |
| `src/main/java/com/growthplanet/interceptor/JwtInterceptor.java` | 鉴权 + 角色校验 + 黑名单 + 注入 UserContext |
| `src/main/java/com/growthplanet/controller/AuthController.java` | AUTH 组 3 端点 |
| `src/main/java/com/growthplanet/controller/FamilyController.java` | FAMILY 组 4 端点 |
| `src/main/java/com/growthplanet/controller/ComplianceController.java` | COMPLIANCE 组 4 端点 |
| `src/main/resources/application.yml` | 主配置（datasource/redis/mybatis-plus/jwt） |
| `src/main/resources/application-dev.yml` | 开发 profile（mock wechat、本地库） |
| `src/main/resources/application-test.yml` | 测试 profile（Testcontainers MySQL） |
| `src/test/java/com/growthplanet/JwtUtilTest.java` | 单元：签发/解析/过期/篡改 |
| `src/test/java/com/growthplanet/InviteCodeUtilTest.java` | 单元：码格式/去重 |
| `src/test/java/com/growthplanet/LogicDeleteTest.java` | 单元：delete_at 重建语义 |
| `src/test/java/com/growthplanet/AuthFlowIT.java` | 集成：登录→角色→档案（Testcontainers） |
| `src/test/java/com/growthplanet/FamilyFlowIT.java` | 集成：创建→邀请→加入→审批 + 越权 403（E-009） |
| `src/test/java/com/growthplanet/ComplianceFlowIT.java` | 集成：同意提交→撤回降级（E-010） |

---

## 3. 数据模型（6 表 → Entity 字段映射）

### 3.1 DDL（`sql/sprint1_schema.sql`）
> 约定：所有表含 `id BIGINT PK`、`create_time DATETIME`、`update_time DATETIME`、`delete_at BIGINT DEFAULT 0`。业务唯一键统一 `UNIQUE(biz_col, delete_at)`（实现"删了还能重建"）。字符集 `utf8mb4`。

```sql
-- ============ usr_user ============
CREATE TABLE usr_user (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  openid        VARCHAR(64)  NOT NULL,
  unionid       VARCHAR(64)  DEFAULT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'UNSET',   -- CHILD/PARENT/ADMIN/UNSET
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar_url    VARCHAR(512) DEFAULT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL/DISABLED
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_openid (openid, delete_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_family ============
CREATE TABLE usr_family (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_name        VARCHAR(64) NOT NULL,
  owner_user_id      BIGINT      NOT NULL,              -- 创建者(家长)
  invite_code        VARCHAR(6)  DEFAULT NULL,          -- 6位大写字母数字
  invite_code_expire BIGINT      DEFAULT NULL,          -- 毫秒时间戳, 24h
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_invite_code (invite_code, delete_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_family_member ============
CREATE TABLE usr_family_member (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id     BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  relation_label VARCHAR(32) DEFAULT NULL,             -- 关系标注(爸/妈/娃)
  role          VARCHAR(16) NOT NULL,                  -- CHILD/PARENT
  bind_status   VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
  guardian_status VARCHAR(16) DEFAULT 'NOT_REQUIRED',  -- NOT_REQUIRED/PENDING/APPROVED/REVOKED
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_family_user (family_id, user_id, delete_at),
  KEY idx_family (family_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_child_profile ============
CREATE TABLE usr_child_profile (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT      NOT NULL,                  -- 关联儿童账户(唯一)
  family_id     BIGINT      NOT NULL,
  nickname      VARCHAR(64) DEFAULT NULL,
  grade         VARCHAR(32) DEFAULT NULL,
  school        VARCHAR(128) DEFAULT NULL,
  allergies     JSON        DEFAULT NULL,              -- List<String> 忌口
  dislikes      JSON        DEFAULT NULL,              -- List<String> 不爱吃
  tastes        JSON        DEFAULT NULL,              -- List<String> 偏好
  profile_status VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE', -- INCOMPLETE/COMPLETED
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user (user_id, delete_at),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_consent_log ============
CREATE TABLE usr_consent_log (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT      NOT NULL,             -- 监护人(家长)
  child_id           BIGINT      NOT NULL,             -- 儿童
  family_id          BIGINT      NOT NULL,
  consent_type       VARCHAR(32) NOT NULL,            -- 如 ORDER/PROFILE
  action             VARCHAR(16) NOT NULL,            -- GRANT/REVOKE
  version            VARCHAR(16) NOT NULL,            -- 同意书版本
  self_reported_age  TINYINT     DEFAULT NULL,         -- 自报年龄核验
  guardian_status    VARCHAR(16) NOT NULL,           -- PENDING/APPROVED/REVOKED
  signed_at          BIGINT      DEFAULT NULL,
  expire_at          BIGINT      DEFAULT NULL,
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  KEY idx_child (child_id),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ sys_audit_log ============
CREATE TABLE sys_audit_log (
  id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT     DEFAULT NULL,              -- 操作人
  family_id    BIGINT      DEFAULT NULL,
  action       VARCHAR(32) NOT NULL,                 -- LOGIN/ROLE/CREATE_FAMILY/JOIN/BIND/GRANT/REVOKE/EXPORT...
  target_type  VARCHAR(32) DEFAULT NULL,
  target_id    BIGINT      DEFAULT NULL,
  ip           VARCHAR(64) DEFAULT NULL,
  detail       VARCHAR(1024) DEFAULT NULL,
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at    BIGINT      NOT NULL DEFAULT 0,
  KEY idx_actor (actor_user_id),
  KEY idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 Entity 要点（以 ChildProfile 为例，JSON 字段用 JacksonTypeHandler）
```java
@Data
@TableName("usr_child_profile")
public class ChildProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long familyId;
    private String nickname;
    private String grade;
    private String school;
    @TableField(typeHandler = JacksonTypeHandler.class)   // 映射 List<String> ↔ JSON 列
    private List<String> allergies;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> dislikes;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tastes;
    private String profileStatus;       // ProfileStatusEnum
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "0", delval = "UNIX_TIMESTAMP() * 1000")  // 若未用全局配置则逐字段标注
    private Long deleteAt = 0L;
}
```
> 其余 5 个 Entity 同构：`@TableName` 映射表名，`@TableId(AUTO)`，普通字段直接映射，`deleteAt` 按 §1.3 配置逻辑删除。JSON 列仅 `usr_child_profile` 有（allergies/dislikes/tastes）。
> `usr_user.role` 初始为 `UNSET`，`select-role` 后更新为 CHILD/PARENT/ADMIN 并重新签发 token。

### 3.3 枚举与邀请码
- **角色 `RoleEnum`**：CHILD / PARENT / ADMIN / UNSET（未选角色前的过渡态；带 UNSET 的 token 访问需角色的接口将被拦截器拒为 403）。
- **bind_status（usr_family_member）**：PENDING→APPROVE/REJECT；**guardian_status**：NOT_REQUIRED/PENDING/APPROVED/REVOKED；**profile_status**：INCOMPLETE/COMPLETED；**usr_consent_log.action**：GRANT/REVOKE；**guardian_status**：PENDING/APPROVED/REVOKED。
- **邀请码**：`InviteCodeUtil.randomCode()` 生成 `[A-Z0-9]{6}`；写库前循环检测 `uk_invite_code` 唯一（碰撞则重生成，最多 5 次）；`invite_code_expire = now + 24h(毫秒)`；过期由 `join` 接口校验 `expire_at > System.currentTimeMillis()`，否则 E-003。

---

## 4. 接口契约（AUTH / FAMILY / COMPLIANCE）

> 统一前缀 `/api`；统一响应 `{code,data,message}`，`code=0` 成功，非 0 见 §8 错误码表。所有接口（除 wx-login）需 `Authorization: Bearer <token>`。

### 4.1 AUTH 组
| 方法 | 路径 | 角色 | 请求 | 响应 data | 错误码 |
|------|------|------|------|-----------|--------|
| POST | `/api/auth/wx-login` | 公开 | `{code:String}` | `{token, openid, role, isNew}` | E-001(无 code) |
| POST | `/api/auth/select-role` | 登录后(UNSET→目标) | `{role:"CHILD"\|"PARENT"}` | `{role, token, nextStep}` | E-001 |
| POST | `/api/child/profile` | CHILD | `{nickname,grade,school,allergies[],dislikes[],tastes[]}` | `{profileStatus:"COMPLETED"}` | E-001,E-002(非儿童),E-009 |

- `wx-login`：调 `WechatClient.code2Session(code)` 取 openid → 按 `(openid, delete_at=0)` 查/建 `usr_user`（role 默认 UNSET）→ 签发 token（family_ids 空）→ 写 `sys_audit_log action=LOGIN`。
- `select-role`：更新 `usr_user.role` 并**重新签发 token**（携带最新角色）。ADMIN 由后台 seed，不在前端自选。
- `child-profile`：`@RequireRole(CHILD)`，写 `usr_child_profile`（首次 INSERT，后续 UPDATE），`profile_status=COMPLETED`。

### 4.2 FAMILY 组
| 方法 | 路径 | 角色 | 请求 | 响应 data | 错误码 |
|------|------|------|------|-----------|--------|
| POST | `/api/family/create` | PARENT | `{familyName}` | `{familyId, inviteCode, expireAt}` | E-001,E-009 |
| GET | `/api/family/invite-code` | PARENT | — | `{inviteCode, expireAt, qrBase64?}` | E-001,E-003(过期则重生成) |
| POST | `/api/family/join` | CHILD | `{inviteCode}` | `{applyId, status:"PENDING"}` | E-001,E-003(码过期),E-009 |
| POST | `/api/family/bind-approve` | PARENT | `{applyId, relationLabel, approve:bool}` | `{bindStatus}` | E-001,E-009 |

- `create`：INSERT `usr_family`（owner=当前家长，生成 inviteCode/expire）→ INSERT `usr_family_member`（family_id, user_id=家长, role=PARENT, bind_status=APPROVED, guardian_status=NOT_REQUIRED）→ 回写 `family_id` 到 token？Sprint 1 家长创建后 family_ids 可为空，前端下次拿新 token 或 `/invite-code` 时附带；**约定**：`create` 返回后由前端用返回 `familyId` 调一次轻量 `/api/auth/token-refresh`（预留）或下次登录刷新。为简化，**Sprint 1 采取"创建后家长主动重新登录/刷新"或"在 create 响应里直接返回带 familyId 的新 token"**——推荐后者：create 成功后重新签发 token 并写入 `family_ids=[familyId]`。（见 §9）
- `invite-code`：查本家庭 `invite_code`；若过期则重生成（24h）并更新。qrBase64 为可选（若需，引入 `com.google.zxing` 生成邀请码/入族链接二维码，否则返回 null，前端自行渲染）。
- `join`：CHILD 校验码有效 → INSERT `usr_family_member`（role=CHILD, bind_status=PENDING, guardian_status=PENDING）→ 返回 applyId。
- `bind-approve`：PARENT 按 applyId 查记录，校验 `family_id` 属本人 → `approve=true` 置 APPROVED，否则 REJECTED；写 `sys_audit_log action=BIND`。

### 4.3 COMPLIANCE 组
| 方法 | 路径 | 角色 | 请求 | 响应 data | 错误码 |
|------|------|------|------|-----------|--------|
| GET | `/api/compliance/consent` | PARENT | —（或 `?childId=`） | `{agreementText, version:"v1", currentStatus}` | E-001 |
| POST | `/api/compliance/consent` | PARENT | `{version, selfReportedAge:int, agreed:bool}` | `{guardianStatus}` | E-001,E-004(年龄<18 拒绝),E-009 |
| POST | `/api/compliance/consent/revoke` | PARENT | `{consentType, childId}` | `{status:"REVOKED"}` | E-001,E-010 |
| POST | `/api/compliance/data-export` | PARENT | `{childId}` | `{taskId, status:"DONE", data}` | E-001,E-009 |

- `consent`（POST）：自报年龄 `selfReportedAge >= 18` 才允许 `agreed=true`（否则 E-004）；INSERT `usr_consent_log(action=GRANT, guardian_status=APPROVED, self_reported_age)`；写 `sys_audit_log action=GRANT`。
- `revoke`：UPDATE 对应 consent_log `action=REVOKE, guardian_status=REVOKED`；同时把该家长当前 token 的 `jti` 加入 Redis 黑名单（即时降级）；返回 E-010 业务码（HTTP 409）。儿童端后续访问被降级提示。
- `data-export`：聚合 `usr_child_profile` + `usr_consent_log`（该 child）组装 JSON 返回 `status=DONE`，并写 `sys_audit_log action=EXPORT`。Sprint 1 仅"入口+基础导出"（内联返回，不做异步文件）。

---

## 5. JWT 鉴权方案（三角色签发/校验/刷新 + 越权 403）

### 5.1 签发（JwtUtil）
- Claims：`user_id`(Long)、`role`(String)、`family_ids`(List<Long>)、`jti`(String, UUID)、`exp`(签发+accessTtl)。
- 算法：HS256（jjwt 0.12.x：`Jwts.builder().subject(...).claim(...).signWith(Keys.hmacShaKeyFor(secret))`）；secret 来自 `JwtProperties`（≥256bit，生产放配置中心）。
- `select-role` / `create` 后调用 `JwtUtil.reissue(loginUser)` 重新签发并带回新角色/新 family_ids。

### 5.2 校验（JwtInterceptor，HandlerInterceptor）
1. `preHandle`：`excludePathPatterns` 含 `/api/auth/wx-login`、 `/actuator/**`、`/error`。
2. 取 `Authorization` → 缺/格式错 → 抛 `BizException(E-001)`（HTTP 401）。
3. `JwtUtil.parse` 失败（签名错/过期/篡改）→ E-001。
4. 查 Redis 黑名单 `jwt:blacklist:{jti}` 命中 → E-001（撤回即时降级）。
5. 构建 `LoginUser` 注入 `UserContext.set(...)`。
6. 若 handler 带 `@RequireRole(...)` 且 `loginUser.role` 不在注解集合 → 抛 `BizException(E-009)`（HTTP 403）。
7. `afterCompletion`：`UserContext.clear()`（防线程复用串号）。

### 5.3 越权触发（E-009）
- 角色不符：`@RequireRole(CHILD)` 的接口被 PARENT 调 → 403。
- 数据越权：Service 层所有查询强制 `.eq("family_id", UserContext.get().familyId())`；儿童接口额外 `.eq("user_id", ctx.userId())`。跨家庭访问因过滤后无数据，按需抛 E-009 或返回空（家庭详情类接口在取到记录后比对 `family_id` 不一致→E-009）。

### 5.4 公开接口放行
- `wx-login` 在 `WebMvcConfig` 的 `excludePathPatterns` 中放行；其余一律过拦截器。

---

## 6. 任务列表（有序、含依赖、建议实现顺序）

> 依赖图：`T01 → T02 → T03 → T04 → T08 → {T05,T06} → T07`；`T10` 依赖 `T03,T04`；`T11` 依赖 `T05,T06,T07`；`T12` 收口。括号内为 P0/P1 优先级。

| 任务 | 名称 | 涉及文件（来自 §2） | 依赖 | 优先级 |
|------|------|---------------------|------|--------|
| **T01** | 项目脚手架与基础设施 | `pom.xml`、`GrowthPlanetApplication.java`、`config/MybatisPlusConfig.java`、`config/WebMvcConfig.java`(空壳)、`config/JwtConfig.java`、`config/RedisConfig.java`、`resources/application*.yml` | 无 | P0 |
| **T02** | 公共层（统一响应/错误码/异常/上下文/角色与状态枚举/注解） | `common/result/*`、`common/exception/*`、`common/context/*`、`common/enums/*`、`common/annotation/RequireRole.java` | T01 | P0 |
| **T03** | 数据层（6 表 DDL + Entity + Mapper + 逻辑删除与自动填充） | `sql/sprint1_schema.sql`、`entity/*`(6)、`mapper/*`(6)、`config/MybatisPlusConfig.java`(MetaObjectHandler)、`util/JsonUtils.java` | T01 | P0 |
| **T04** | JWT 工具与拦截器 + 微信客户端抽象 | `util/JwtUtil.java`、`interceptor/JwtInterceptor.java`、`service/WechatClient.java`、`service/impl/MockWechatClient.java`、`service/impl/WxWechatClient.java`、`config/WebMvcConfig.java`(注册拦截器) | T02 | P0 |
| **T08** | 审计服务（sys_audit_log 基础写入） | `service/AuditService.java`+`impl`、`entity/AuditLog.java`、`mapper/AuditLogMapper.java` | T03 | P0 |
| **T05** | AUTH 模块（微信登录/角色选择/儿童档案） | `service/AuthService.java`+`impl`、`controller/AuthController.java`、`dto/request/{WxLogin,SelectRole,ChildProfile}Req.java`、`dto/response/{WxLogin,SelectRole,ChildProfile}Resp.java` | T02,T03,T04,T08 | P0 |
| **T06** | FAMILY 模块（创建/邀请码/加入/绑定审批） | `service/FamilyService.java`+`impl`、`controller/FamilyController.java`、`util/InviteCodeUtil.java`、`dto/request/{CreateFamily,JoinFamily,BindApprove}Req.java`、`dto/response/{CreateFamily,InviteCode,JoinFamily,BindApprove}Resp.java` | T02,T03,T04,T08 | P0 |
| **T07** | COMPLIANCE 模块（同意查询/提交/撤回/数据导出） | `service/ComplianceService.java`+`impl`、`controller/ComplianceController.java`、`dto/request/{Consent,RevokeConsent,DataExport}Req.java`、`dto/response/{Consent,DataExport}Resp.java` | T02,T03,T04,T06,T08 | P0 |
| **T09** | 配置与可运行性收口（profile/datasource/redis 连通、启动自检） | `resources/application-dev.yml`、`application-test.yml`、README 启动说明 | T01 | P0 |
| **T10** | 单元测试（JWT/邀请码/逻辑删除重建语义） | `test/.../JwtUtilTest.java`、`InviteCodeUtilTest.java`、`LogicDeleteTest.java` | T03,T04 | P1 |
| **T11** | 集成测试（AUTH/FAMILY/COMPLIANCE 关键路径 + 越权403 + 撤回降级） | `test/.../AuthFlowIT.java`、`FamilyFlowIT.java`、`ComplianceFlowIT.java`（Testcontainers MySQL） | T05,T06,T07 | P0 |
| **T12** | 代码评审与 DoD 自查 | —（评审清单见 §8 共享知识 / DoD） | T05~T11 | P0 |

**建议实现顺序**：T01 → T02 → T03 → T04 → T08 → T05 → T06 → T07 → T09 → T10 → T11 → T12。

---

## 7. 依赖包列表（pom.xml 草案，完整可解析）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.growthplanet</groupId>
    <artifactId>growth-planet</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>growth-planet</name>

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <jjwt.version>0.12.7</jjwt.version>
        <testcontainers.version>1.20.4</testcontainers.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Web (Spring MVC, Jakarta EE 11) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Jakarta 校验 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MyBatis-Plus 专用 Spring Boot 4 starter（3.5.15+ 兼容 SB4/Spring Framework 7） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL 驱动（坐标 mysql-connector-j，由 SB4 BOM 管理解析到 9.x） -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- Redis（JWT 黑名单/refresh 预留；Lettuce） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- JWT (jjwt 0.12.x) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <!-- Testcontainers（集成测试用真实 MySQL，避免 H2 不支持 JSON/函数） -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <!-- Lombok 在 SB4 下需配合该插件（含 annotationProcessor） -->
            </plugin>
        </plugins>
    </build>
</project>
```
> 构建命令（本机 JDK 21）：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home /opt/homebrew/bin/mvn -f /Users/zhouwei/my-workspace/umbrella/growth-planet/pom.xml clean package`
> 可选二维码依赖（若 invite-code 需 qrBase64）：`com.google.zxing:core` + `com.google.zxing:javase`（Sprint 1 不强求）。

---

## 8. 共享知识（跨文件约定，工程师必读）

1. **统一响应**：所有 Controller 返回 `Result<T>`。`Result.ok(data)` / `Result.fail(ResultCode)`。禁止直接返回裸对象或 `Map`。
2. **错误码枚举**：`ResultCode` 含 `code(int)` / `httpStatus` / `message`。映射：
   | 枚举 | code | HTTP | 含义 |
   |------|------|------|------|
   | E001_NO_WX_AUTH | 1001 | 401 | 未登录或登录失效 |
   | E002_PROFILE_INCOMPLETE | 1002 | 200 | 档案未补全（warn，前端灰显引导） |
   | E003_INVITE_CODE_EXPIRED | 1003 | 400 | 邀请码过期 |
   | E004_GUARDIAN_VERIFY_FAILED | 1004 | 400 | 监护人核验失败（年龄<18） |
   | E005_SUBMIT_TIMEOUT | 1005 | 409 | 提交超时（预留） |
   | E006_BALANCE_INSUFFICIENT | 1006 | 409 | 虚拟余额不足（Sprint1 预留，不触发） |
   | E007_CONCURRENCY_CONFLICT | 1007 | 409 | 并发冲突（预留） |
   | E008_NOTICE_FAILED | 1008 | 200 | 通知失败（预留，warn） |
   | E009_FORBIDDEN | 1009 | 403 | 越权访问 |
   | E010_CONSENT_REVOKED | 1010 | 409 | 监护人撤回同意，能力降级 |
   `GlobalExceptionHandler` 捕获 `BizException` → 用其 `ResultCode` 的 `httpStatus` 与 `code` 组装 `Result`。
3. **逻辑删除统一**：所有 Entity 的 `deleteAt` 按 §1.3 配置；禁止手写物理删除（除非审计类 `sys_audit_log` 明确需要，本 Sprint 不删）。
4. **通用字段自动填充**：`create_time`/`update_time` 由 `MetaObjectHandler` 在 insert/update 时填 `LocalDateTime.now()`；`delete_at` 不在此填充。
5. **事务边界**：`select-role`、`create`、`join`、`bind-approve`、`consent`(POST)、`revoke` 等写多表操作标 `@Transactional`；读操作不加。
6. **数据隔离统一过滤**：每个 Service 方法开头取 `UserContext.get().familyId()`（儿童接口还要 `userId()`），所有 `QueryWrapper`/`UpdateWrapper` 必须 `.eq("family_id", familyId)` + `.eq("delete_at", 0)`（delete_at 由逻辑删除自动加，但仍显式声明以防漏）。跨家庭记录取出后二次比对 `family_id` 不一致→抛 E-009。
7. **审计点**：登录(LOGIN)、选角色(ROLE)、建家庭(CREATE_FAMILY)、加入(JOIN)、绑定(BIND)、同意(GRANT)、撤回(REVOKE)、导出(EXPORT) 均调 `AuditService.record(...)` 写 `sys_audit_log`。
8. **JSON 列**：`usr_child_profile` 的 allergies/dislikes/tastes 用 `JacksonTypeHandler` + `List<String>`；API DTO 也用 `List<String>`。
9. **金额**：本 Sprint 无金额字段；未来 `DECIMAL(10,2)` 一律标注"虚拟"，非空业务。

### 8.1 DoD 自查清单（T12 评审用）
- [ ] 登录→角色→档案→家庭→绑定→同意 全链路可跑通（AuthFlowIT/FamilyFlowIT/ComplianceFlowIT 绿）
- [ ] 越权访问返回 403（E-009）：角色不符 + 跨家庭数据访问
- [ ] 同意撤回即时降级（E-010）：revoke 后该家长 token 入黑名单、儿童端被降级
- [ ] 单元/集成测试覆盖 AUTH/FAMILY/COMPLIANCE 关键路径
- [ ] 代码评审通过（分层清晰、无裸返回、错误码统一、数据隔离到位）

---

## 9. 待明确事项（需用户/主理人拍板或构建期实测）

1. **`usr_user.openid` 唯一键形态**（已按裁定采用复合唯一，需确认）：设计文档原文写 `UNIQUE(openid)` 单列；本设计按"删了还能重建"裁定改为 `UNIQUE(openid, delete_at)`。若用户坚持单列 UNIQUE，则删除用户后 openid 被占用无法重建——需明确取舍。**建议：采用复合唯一（本设计已落地）。**
2. **微信 code2Session 是否需真实 appid/secret**：Sprint 1 已用 `MockWechatClient`（dev/test 默认）规避；生产需在 `application-prod.yml` 配 `wx.appid`/`wx.secret` 并切 `WxWechatClient`。**建议：Sprint 1 先用 mock，真实配置 Sprint 4 前补齐。**
3. **Redis 本期真实启用 vs 仅预留**：本设计"启用"，核心用于 JWT 黑名单。若环境无 Redis，拦截器 fail-open 放行（见 §1.4），但撤回即时降级将退化为"仅 DB guardian_status 标记、下次请求校验"，降级力度减弱。**建议：启用 Redis，CI 起一个 Redis 容器。**
4. **家庭创建后家长 token 如何带 family_id**：本设计采用"create 成功后立即重新签发 token 写入 family_ids 并返回"（推荐，零额外交互）；备选为独立 refresh 端点。**需确认采用哪种。**
5. **`data-export` 导出格式**：Sprint 1 内联返回 JSON；是否需要 CSV/文件下载？**建议：Sprint 1 仅 JSON 内联。**
6. **邀请码二维码 qrBase64**：是否本期需要？需要则加 `com.google.zxing` 依赖。**建议：前端用 inviteCode 自行渲染，后端返回 null。**
7. **MyBatis-Plus 3.5.17 + SB4.1.1 实测**：坐标已核验存在于中央仓，但构建期仍建议 `mvn -U clean package` 实测；若中央仓某镜像不可解析，按 Q-06 回落最近 GA（如 3.5.16）并在本文件标注——**不要擅自锁 3.x**。
8. **多家庭假设**：V0.0.1 假设单家庭绑定（uk_family_user 约束）；若需多家庭，`token.family_ids[]` 已支持，但 `bind-approve`/查询需按"当前选中的 family_id"处理，本期未展开。

---

## 10. IS_PASS 判断

**IS_PASS = ✅ PASS**

- 设计自洽：技术栈版本均经网络核验可解析（Spring Boot 4.1.1 / mybatis-plus-spring-boot4-starter 3.5.17 / jjwt 0.12.7 / mysql-connector-j 9.x）；分层、接口契约、数据模型、JWT 方案、逻辑删除方案、错误码、任务分解彼此一致，无循环依赖或歧义阻塞。
- 可直接编码：文件清单（§2）、接口契约（§4）、DDL（§3.1）、pom（§7）、共享约定（§8）足够工程师无需回看设计文档即可实现；12 个任务（§6）依赖清晰、顺序合理。
- 残余风险均为低风险可回落项（§9），不影响 Sprint 1 编码启动；其中第 7 项要求构建期实测但已给出回落路径。

---

### 附：类图（抽取至 `class-diagram.mermaid`）
### 附：时序图（抽取至 `sequence-diagram.mermaid`）
