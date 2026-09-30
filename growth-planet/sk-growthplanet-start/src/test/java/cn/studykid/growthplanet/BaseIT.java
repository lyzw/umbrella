package cn.studykid.growthplanet;

import cn.studykid.growthplanet.config.RequestContextFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import cn.studykid.growthplanet.common.context.LoginUser;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyInviteResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.SelectRoleResp;
import cn.studykid.growthplanet.dto.response.WxLoginResp;
import cn.studykid.growthplanet.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 集成测试基类：通过 Testcontainers 拉起 MySQL + Redis。
 * MySQLContainer / GenericContainer(Redis) 经 @DynamicPropertySource 注入数据源与 Redis 连接信息。
 * 使用 MockMvc 走完整 DispatcherServlet（含 JwtInterceptor）。
 * <p>
 * 本项目由 WebApplicationContext 手动构建 MockMvc，GenericContainer 承载 Redis。
 */
@SpringBootTest
@ContextConfiguration(initializers = BaseIT.LocalDbResetInitializer.class)
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIT {

    /**
     * 本地调试开关：设 USE_LOCAL_MYSQL=true 时复用本机已启动的 MySQL+Redis，
     * 不再拉起 Testcontainers 容器（适用于 Docker 不可用 / 磁盘紧张场景）。
     * 默认 false，仍走 Testcontainers，CI 行为不变。
     */
    static final boolean USE_LOCAL_MYSQL =
            Boolean.parseBoolean(System.getenv("USE_LOCAL_MYSQL"));

    /** 本地集成测试库（独立库，隔离用户 growth_planet 业务库）。 */
    private static final String LOCAL_MYSQL_URL =
            "jdbc:mysql://localhost:3306/growth_planet_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    /**
     * 本地调试用：在 Spring 上下文刷新前清空并重建独立测试库，保证
     * spring.sql.init（建表脚本）始终在空库上幂等执行。
     * Testcontainers 模式（USE_LOCAL_MYSQL=false）下不触碰本地库。
     */
    static class LocalDbResetInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            if (!USE_LOCAL_MYSQL) {
                return;
            }
            try (Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    "root", "123456")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("DROP DATABASE IF EXISTS growth_planet_it");
                    st.execute("CREATE DATABASE growth_planet_it DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
            } catch (SQLException e) {
                throw new IllegalStateException("本地测试库 growth_planet_it 重置失败", e);
            }
        }
    }

    static MySQLContainer<?> MYSQL;
    static GenericContainer<?> REDIS;

    static {
        if (!USE_LOCAL_MYSQL) {
            MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                    .withUrlParam("useSSL", "false")
                    .withUrlParam("allowPublicKeyRetrieval", "true")
                    .withUrlParam("serverTimezone", "UTC");
            MYSQL.start();
            REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
                    .withExposedPorts(6379);
            REDIS.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { if (REDIS != null) REDIS.stop(); } catch (Exception ignored) { }
                try { if (MYSQL != null) MYSQL.stop(); } catch (Exception ignored) { }
            }));
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        if (USE_LOCAL_MYSQL) {
            registry.add("spring.datasource.url", () -> LOCAL_MYSQL_URL);
            registry.add("spring.datasource.username", () -> "root");
            registry.add("spring.datasource.password", () -> "123456");
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.data.redis.host", () -> "localhost");
            registry.add("spring.data.redis.port", () -> "6379");
            registry.add("spring.data.redis.database", () -> "0");
        } else {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
            registry.add("spring.data.redis.database", () -> "0");
        }
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean(RequestContextFilter.class)).build();
    }

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected RedisTemplate<String, String> redisTemplate;

    protected static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    /** 解析统一响应体中的 data 字段为指定类型。 */
    @SuppressWarnings("unchecked")
    protected <T> T dataOf(MvcResult result, Class<T> clazz) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return objectMapper.convertValue(root.get("data"), clazz);
    }

    /** 微信登录 + 选择角色，返回角色 token。 */
    protected String loginAndSelectRole(String code, RoleEnum role) throws Exception {
        MvcResult wx = mockMvc.perform(post("/api/mini/auth/wx-login")
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        WxLoginResp wxResp = dataOf(wx, WxLoginResp.class);

        MvcResult sr = mockMvc.perform(post("/api/mini/auth/select-role")
                        .header("Authorization", "Bearer " + wxResp.getToken())
                        .contentType(JSON)
                        .content("{\"role\":\"" + role.name() + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        SelectRoleResp srResp = dataOf(sr, SelectRoleResp.class);
        return srResp.getToken();
    }

    /** 构建带指定 jti 的测试 token。 */
    protected String tokenWithJti(Long userId, RoleEnum role, List<Long> familyIds, String jti) {
        LoginUser u = LoginUser.builder()
                .userId(userId)
                .role(role.name())
                .familyIds(familyIds)
                .jti(jti)
                .build();
        return jwtUtil.generateToken(u);
    }

    /** 家庭上下文：包含父子 token、familyId、邀请码、子加入申请 ID。 */
    public record FamilyContext(String parentToken, String childToken, Long familyId,
                                String inviteCode, Long applyId) {
    }

    /** 搭建一个「家长已建家庭、儿童已加入待审批」的最小上下文。 */
    protected FamilyContext setupFamily() throws Exception {
        String parentCode = "p_" + UUID.randomUUID();
        String childCode = "c_" + UUID.randomUUID();
        String parentToken = loginAndSelectRole(parentCode, RoleEnum.PARENT);
        String childToken = loginAndSelectRole(childCode, RoleEnum.CHILD);

        MvcResult create = mockMvc.perform(post("/api/mini/family/create")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(JSON)
                        .content("{\"familyName\":\"测试家庭\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        CreateFamilyResp cf = dataOf(create, CreateFamilyResp.class);

        // 生成「学生邀请码」（CHILD 类型，需审批），供儿童以 CHILD 角色加入本家庭。
        MvcResult childInv = mockMvc.perform(post("/api/mini/family/invite-code")
                        .header("Authorization", "Bearer " + cf.getToken())
                        .contentType(JSON)
                        .content("{\"inviteType\":\"CHILD\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        FamilyInviteResp childInvResp = dataOf(childInv, FamilyInviteResp.class);

        MvcResult join = mockMvc.perform(post("/api/mini/family/join")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"inviteCode\":\"" + childInvResp.getInviteCode() + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        JoinFamilyResp jf = dataOf(join, JoinFamilyResp.class);

        return new FamilyContext(cf.getToken(), childToken, cf.getFamilyId(), childInvResp.getInviteCode(), jf.getApplyId());
    }

    protected Long childUserId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.childToken()));
    }

    protected String consentJson(FamilyContext ctx) {
        return "{\"childId\":\"" + childUserId(ctx) + "\",\"applyId\":\"" + ctx.applyId()
                + "\",\"consentType\":\"PROFILE\",\"version\":\"v1\",\"selfReportedAge\":25,\"agreed\":true}";
    }

    protected void grant(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/mini/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(consentJson(ctx)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    protected void approve(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/mini/family/bind-approve").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    protected String profileJson(FamilyContext ctx) {
        return "{\"childId\":\"" + childUserId(ctx)
                + "\",\"nickname\":\"测试儿童\",\"grade\":\"三年级\",\"school\":\"合成学校\","
                + "\"allergies\":[\"PEANUT\"],\"dislikes\":[\"胡萝卜\"],\"tastes\":[\"清淡\"]}";
    }

    protected void revoke(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/mini/compliance/consent/revoke").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"childId\":\"" + childUserId(ctx)
                        + "\",\"consentType\":\"PROFILE\",\"version\":\"v1\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
