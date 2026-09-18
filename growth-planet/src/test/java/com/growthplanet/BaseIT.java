package com.growthplanet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.growthplanet.common.context.LoginUser;
import com.growthplanet.common.enums.RoleEnum;
import com.growthplanet.dto.response.CreateFamilyResp;
import com.growthplanet.dto.response.JoinFamilyResp;
import com.growthplanet.dto.response.SelectRoleResp;
import com.growthplanet.dto.response.WxLoginResp;
import com.growthplanet.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
@Testcontainers
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIT {

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("serverTimezone", "UTC");

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.database", () -> "0");
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean(com.growthplanet.config.RequestContextFilter.class)).build();
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
        MvcResult wx = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        WxLoginResp wxResp = dataOf(wx, WxLoginResp.class);

        MvcResult sr = mockMvc.perform(post("/api/auth/select-role")
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

        MvcResult create = mockMvc.perform(post("/api/family/create")
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(JSON)
                        .content("{\"familyName\":\"测试家庭\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        CreateFamilyResp cf = dataOf(create, CreateFamilyResp.class);

        MvcResult join = mockMvc.perform(post("/api/family/join")
                        .header("Authorization", "Bearer " + childToken)
                        .contentType(JSON)
                        .content("{\"inviteCode\":\"" + cf.getInviteCode() + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        JoinFamilyResp jf = dataOf(join, JoinFamilyResp.class);

        return new FamilyContext(cf.getToken(), childToken, cf.getFamilyId(), cf.getInviteCode(), jf.getApplyId());
    }

    protected Long childUserId(FamilyContext ctx) {
        return jwtUtil.getUserId(jwtUtil.parse(ctx.childToken()));
    }

    protected String consentJson(FamilyContext ctx) {
        return "{\"childId\":\"" + childUserId(ctx) + "\",\"applyId\":\"" + ctx.applyId()
                + "\",\"consentType\":\"PROFILE\",\"version\":\"v1\",\"selfReportedAge\":25,\"agreed\":true}";
    }

    protected void grant(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(consentJson(ctx)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    protected void approve(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/family/bind-approve").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    protected String profileJson(FamilyContext ctx) {
        return "{\"childId\":\"" + childUserId(ctx)
                + "\",\"nickname\":\"测试儿童\",\"grade\":\"三年级\",\"school\":\"合成学校\","
                + "\"allergies\":[\"PEANUT\"],\"dislikes\":[\"胡萝卜\"],\"tastes\":[\"清淡\"]}";
    }

    protected void revoke(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/compliance/consent/revoke").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"childId\":\"" + childUserId(ctx)
                        + "\",\"consentType\":\"PROFILE\",\"version\":\"v1\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
