package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.context.UserContext;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.entity.User;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.response.SelectRoleResp;
import cn.studykid.growthplanet.dto.response.WxLoginResp;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityRegressionIT extends BaseIT {
    @Autowired
    UserMapper users;
    @Autowired
    FamilyMemberMapper members;
    @Autowired
    NoticeMapper notices;
    @Autowired
    AuditLogMapper audits;
    @Autowired
    ComplianceProperties policy;

    @Test
    void roleCannotSwitchOldTokenExpiresAndLogoutIsDurable() throws Exception {
        var login = mockMvc.perform(post("/api/mini/auth/wx-login").contentType(JSON)
                .content("{\"code\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isOk()).andReturn();
        String original = dataOf(login, WxLoginResp.class).getToken();
        var select = mockMvc.perform(post("/api/mini/auth/select-role").header("Authorization", "Bearer " + original)
                .contentType(JSON).content("{\"role\":\"CHILD\"}")).andExpect(status().isOk()).andReturn();
        String child = dataOf(select, SelectRoleResp.class).getToken();
        mockMvc.perform(post("/api/mini/auth/select-role").header("Authorization", "Bearer " + original)
                .contentType(JSON).content("{\"role\":\"CHILD\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/mini/auth/select-role").header("Authorization", "Bearer " + child)
                .contentType(JSON).content("{\"role\":\"PARENT\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mini/auth/logout").header("Authorization", "Bearer " + child)).andExpect(status().isOk());
        mockMvc.perform(post("/api/mini/auth/logout").header("Authorization", "Bearer " + child)).andExpect(status().isUnauthorized());
    }

    @Test
    void reloginRestoresFamiliesAndDisabledAccountsCannotLoginOrUseToken() throws Exception {
        String code = UUID.randomUUID().toString();
        String parent = loginAndSelectRole(code, RoleEnum.PARENT);
        mockMvc.perform(post("/api/mini/family/create").header("Authorization", "Bearer " + parent)
                .contentType(JSON).content("{\"familyName\":\"合成家庭\"}")).andExpect(status().isOk());
        var login = mockMvc.perform(post("/api/mini/auth/wx-login").contentType(JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk()).andReturn();
        String token = dataOf(login, WxLoginResp.class).getToken();
        assertEquals(1, jwtUtil.getFamilyIds(jwtUtil.parse(token)).size());
        mockMvc.perform(get("/api/mini/family/invite-code").header("Authorization", "Bearer " + parent)).andExpect(status().isOk());
        users.update(null, new UpdateWrapper<User>().eq("id", jwtUtil.getUserId(jwtUtil.parse(token))).set("status", "DISABLED"));
        mockMvc.perform(get("/api/mini/family/invite-code").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/mini/auth/wx-login").contentType(JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectionReusesRelationshipButNotOldConsentAndApprovalsAreIdempotent() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        mockMvc.perform(post("/api/mini/family/bind-approve").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":false}")).andExpect(status().isOk());
        var join = mockMvc.perform(post("/api/mini/family/join").header("Authorization", "Bearer " + ctx.childToken())
                .contentType(JSON).content("{\"inviteCode\":\"" + ctx.inviteCode() + "\"}")).andExpect(status().isOk()).andReturn();
        assertEquals(ctx.applyId().toString(), objectMapper.readTree(join.getResponse().getContentAsString())
                .get("data").get("applyId").asText());
        assertEquals(2, members.selectById(ctx.applyId()).getApplicationVersion());
        mockMvc.perform(post("/api/mini/family/bind-approve").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                .andExpect(status().isConflict());
        grant(ctx);
        approve(ctx);
        approve(ctx);
        assertEquals(2, notices.selectCount(new QueryWrapper<Notice>().eq("child_id", childUserId(ctx))
                .eq("event_type", "BIND_BOUND")));
        var other = setupFamily();
        mockMvc.perform(post("/api/mini/family/join").header("Authorization", "Bearer " + ctx.childToken())
                .contentType(JSON).content("{\"inviteCode\":\"" + other.inviteCode() + "\"}")).andExpect(status().isConflict());
    }

    @Test
    void malformedPayloadHasTraceAndNeverLogsChildData() throws Exception {
        var ctx = setupFamily();
        var result = mockMvc.perform(post("/api/mini/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"nickname\":\"PRIVATE_SENTINEL\","))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-400")).andReturn();
        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertEquals(requestId, objectMapper.readTree(result.getResponse().getContentAsString()).get("requestId").asText());
        AuditLog log = audits.selectOne(new QueryWrapper<AuditLog>().eq("request_id", requestId));
        assertEquals("E-400", log.getErrorCode());
        assertNotNull(log.getIp());
        assertNull(log.getDetail());
        assertNull(UserContext.get());
        mockMvc.perform(get("/api/mini/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .param("childId", "-1").param("consentType", "PROFILE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/mini/compliance/requests/-1").header("Authorization", "Bearer " + ctx.parentToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/mini/not-an-endpoint")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mini/auth/wx-login")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/mini/auth/wx-login").contentType("text/plain").content("synthetic"))
                .andExpect(status().isUnsupportedMediaType());
        var denial = mockMvc.perform(post("/api/mini/family/create")
                .header("Authorization", "Bearer " + ctx.childToken()).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden()).andReturn();
        AuditLog denied = audits.selectOne(new QueryWrapper<AuditLog>()
                .eq("request_id", denial.getResponse().getHeader("X-Request-Id")));
        assertEquals(childUserId(ctx), denied.getActorUserId());
        assertNull(UserContext.get());
    }

    @Test
    void q01GateBlocksIdentityBeforePersistence() throws Exception {
        var ctx = setupFamily();
        policy.setCollectionEnabled(false);
        try {
            long before = users.selectCount(new QueryWrapper<>());
            mockMvc.perform(post("/api/mini/auth/wx-login").contentType(JSON).content("{\"code\":\"closed-gate\"}"))
                    .andExpect(status().isForbidden());
            assertEquals(before, users.selectCount(new QueryWrapper<>()));
            mockMvc.perform(post("/api/mini/family/join").header("Authorization", "Bearer " + ctx.childToken())
                    .contentType(JSON).content("{\"inviteCode\":\"" + ctx.inviteCode() + "\"}"))
                    .andExpect(status().isForbidden());
        } finally {
            policy.setCollectionEnabled(true);
        }
    }

    @Test
    void deletedIdentityCannotLoginOrBeRecreated() throws Exception {
        String code = UUID.randomUUID().toString();
        String token = loginAndSelectRole(code, RoleEnum.CHILD);
        Long userId = jwtUtil.getUserId(jwtUtil.parse(token));
        users.deleteById(userId);
        mockMvc.perform(post("/api/mini/auth/wx-login").contentType(JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(userId, users.findIdentityIncludingDeleted("mock_openid_" + code).getId());
        mockMvc.perform(post("/api/mini/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
