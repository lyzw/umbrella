package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.config.PrivacyProperties;
import cn.studykid.growthplanet.dto.response.DataExportResp;
import cn.studykid.growthplanet.entity.*;
import cn.studykid.growthplanet.mapper.*;
import cn.studykid.growthplanet.service.WechatClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrivacyWorkflowIT extends BaseIT {
    @Autowired PrivacyProperties settings;
    @Autowired PrivacyRequestMapper requests;
    @Autowired UserMapper users;
    @Autowired ChildProfileMapper profiles;
    @Autowired AuditLogMapper audit;
    @MockitoSpyBean WechatClient wechat;

    @AfterEach
    void resetSettings() {
        settings.setOperatorIds(Set.of());
    }

    @Test
    void exportRequiresKeyAndRejectsOtherAccountAndChangedPayload() throws Exception {
        var ctx = boundFamily();
        mockMvc.perform(post("/api/mini/compliance/data-export").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(exportJson(ctx))).andExpect(status().isBadRequest());
        for (String key : new String[]{"bad key", "a".repeat(129)}) {
            mockMvc.perform(post("/api/mini/compliance/data-export").header("Authorization", bearer(ctx.parentToken()))
                    .header("Idempotency-Key", key).contentType(JSON).content(exportJson(ctx)))
                    .andExpect(status().isBadRequest());
        }
        DataExportResp first = export(ctx, "same_key");
        assertEquals(first.getTaskId(), export(ctx, "same_key").getTaskId());
        var other = boundFamily();
        mockMvc.perform(get("/api/mini/compliance/requests/" + first.getTaskId())
                .header("Authorization", bearer(other.parentToken()))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/mini/compliance/requests/" + first.getTaskId() + "/download")
                .header("Authorization", bearer(other.parentToken()))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mini/compliance/data-export").header("Authorization", bearer(other.parentToken()))
                .header("Idempotency-Key", "foreign").contentType(JSON).content(exportJson(ctx)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/mini/compliance/data-delete").header("Authorization", bearer(ctx.parentToken()))
                .header("Idempotency-Key", "same_key").contentType(JSON).content(deleteJson(ctx, "code", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-012"));
    }

    @Test
    void generatedDownloadIsRequesterOnlyExpiresAndStoresNoPlaintextExport() throws Exception {
        var ctx = boundFamily();
        mockMvc.perform(post("/api/mini/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
        ChildProfile profile = profiles.selectOne(new QueryWrapper<ChildProfile>()
                .eq("user_id", childUserId(ctx)));
        profile.setFavoriteDishIds(List.of("PRESET:9007199254740993", "PRESET:42"));
        assertEquals(1, profiles.updateById(profile));
        var task = export(ctx, "download");
        String operator = operator();
        transition(operator, task, "PROCESSING", 0);
        transition(operator, task, "READY", 1);
        profile.setFavoriteDishIds(List.of());
        assertEquals(1, profiles.updateById(profile));
        mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId() + "/download")
                .header("Authorization", bearer(ctx.parentToken()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.favoriteDishIds").isEmpty());
        profile.setFavoriteDishIds(List.of("PRESET:9007199254740993", "PRESET:42"));
        assertEquals(1, profiles.updateById(profile));
        PrivacyRequest request = requests.selectById(Long.valueOf(task.getTaskId()));
        assertEquals("GENERATED_PROFILE_V1", request.getResultRef());
        assertTrue(request.getExpiresAt() > System.currentTimeMillis() + 23L * 3600 * 1000);
        revoke(ctx);
        var download = mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId() + "/download")
                .header("Authorization", bearer(ctx.parentToken()))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.childId").value(childUserId(ctx).toString()))
                .andExpect(jsonPath("$.profile.nickname").value("测试儿童"))
                .andExpect(jsonPath("$.profile.favoriteDishIds.length()").value(2))
                .andExpect(jsonPath("$.profile.favoriteDishIds[0]").value("PRESET:9007199254740993"))
                .andExpect(jsonPath("$.profile.favoriteDishIds[1]").value("PRESET:42"))
                .andExpect(jsonPath("$.openid").doesNotExist())
                .andExpect(jsonPath("$.scope").isString()).andReturn();
        assertFalse(download.getResponse().getContentAsString().contains("sessionKey"));
        mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId() + "/download")
                .header("Authorization", bearer(operator))).andExpect(status().isForbidden());
        requests.update(null, new UpdateWrapper<PrivacyRequest>().eq("id", task.getTaskId()).set("expires_at", 1L));
        mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId() + "/download")
                .header("Authorization", bearer(ctx.parentToken()))).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("E-410"));
        mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId())
                .header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.downloadAvailable").value(false));
        assertEquals(2, audit.selectCount(new QueryWrapper<AuditLog>().eq("action", "PRIVACY_DOWNLOAD")
                .eq("target_id", task.getTaskId())));
        assertEquals(0, audit.selectCount(new QueryWrapper<AuditLog>().eq("target_id", task.getTaskId())
                .like("detail", "测试儿童")));
    }

    @Test
    void ordinaryAdminCannotOperateAndTransitionsRequireVersionAndEvidence() throws Exception {
        var ctx = boundFamily();
        var task = export(ctx, "ops");
        String operator = operator();
        settings.setOperatorIds(Set.of());
        mockMvc.perform(post("/api/mini/admin/compliance/requests/" + task.getTaskId() + "/transition")
                .header("Authorization", bearer(operator)).contentType(JSON)
                .content(transitionJson("PROCESSING", 0))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId())
                .header("Authorization", bearer(operator))).andExpect(status().isForbidden());
        settings.setOperatorIds(Set.of(jwtUtil.getUserId(jwtUtil.parse(operator))));
        mockMvc.perform(post("/api/mini/admin/compliance/requests/" + task.getTaskId() + "/transition")
                .header("Authorization", bearer(operator)).contentType(JSON)
                .content("{\"status\":\"PROCESSING\",\"expectedVersion\":0}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/mini/admin/compliance/requests/" + task.getTaskId() + "/transition")
                .header("Authorization", bearer(operator)).contentType(JSON)
                .content(transitionJson("PROCESSING", 0).replace("ticket_123", "../../secret")))
                .andExpect(status().isBadRequest());
        transition(operator, task, "PROCESSING", 0);
        mockMvc.perform(post("/api/mini/admin/compliance/requests/" + task.getTaskId() + "/transition")
                .header("Authorization", bearer(operator)).contentType(JSON)
                .content(transitionJson("READY", 0))).andExpect(status().isConflict());
        mockMvc.perform(post("/api/mini/admin/compliance/requests/" + task.getTaskId() + "/transition")
                .header("Authorization", bearer(operator)).contentType(JSON)
                .content(transitionJson("COMPLETED", 1))).andExpect(status().isConflict());
        transition(operator, task, "READY", 1);
        assertEquals(2, audit.selectCount(new QueryWrapper<AuditLog>().eq("action", "PRIVACY_TRANSITION")
                .eq("target_id", task.getTaskId())));
    }

    @Test
    void deletionRequiresConfirmationMatchingFreshIdentityAndManualEvidence() throws Exception {
        var ctx = boundFamily();
        mockMvc.perform(post("/api/mini/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
        for (String json : new String[]{deleteJson(ctx, "wrong-account", true),
                deleteJson(ctx, "unused", false)}) {
            mockMvc.perform(post("/api/mini/compliance/data-delete").header("Authorization", bearer(ctx.parentToken()))
                    .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        Long parentId = jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken()));
        String freshCode = "verify_" + UUID.randomUUID();
        var session = new WechatClient.WxSession();
        session.setOpenid(users.selectById(parentId).getOpenid());
        doReturn(session).when(wechat).code2Session(freshCode);
        DataExportResp task = null;
        for (int i = 0; i < 2; i++) {
            var result = mockMvc.perform(post("/api/mini/compliance/data-delete")
                    .header("Authorization", bearer(ctx.parentToken())).header("Idempotency-Key", "delete_1")
                    .contentType(JSON).content(deleteJson(ctx, freshCode, true)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RECEIVED")).andReturn();
            var current = dataOf(result, DataExportResp.class);
            if (task != null) assertEquals(task.getTaskId(), current.getTaskId());
            task = current;
        }
        assertNotNull(task);
        assertNotNull(requests.selectById(Long.valueOf(task.getTaskId())).getVerifiedAt());
        mockMvc.perform(post("/api/mini/compliance/data-delete").header("Authorization", bearer(ctx.parentToken()))
                .header("Idempotency-Key", "new_replayed_code").contentType(JSON)
                .content(deleteJson(ctx, freshCode, true))).andExpect(status().isBadRequest());
        String operator = operator();
        transition(operator, task, "PROCESSING", 0);
        transition(operator, task, "COMPLETED", 1);
        assertEquals(1, profiles.selectCount(new QueryWrapper<ChildProfile>().eq("user_id", childUserId(ctx))));
        assertEquals("ticket_123", requests.selectById(Long.valueOf(task.getTaskId())).getEvidenceRef());
        mockMvc.perform(get("/api/mini/compliance/requests/" + task.getTaskId() + "/download")
                .header("Authorization", bearer(ctx.parentToken()))).andExpect(status().isConflict());
    }

    private FamilyContext boundFamily() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        return ctx;
    }

    private DataExportResp export(FamilyContext ctx, String key) throws Exception {
        return dataOf(mockMvc.perform(post("/api/mini/compliance/data-export")
                .header("Authorization", bearer(ctx.parentToken())).header("Idempotency-Key", key)
                .contentType(JSON).content(exportJson(ctx))).andExpect(status().isOk()).andReturn(), DataExportResp.class);
    }

    private String operator() {
        User user = new User();
        user.setOpenid("operator_" + UUID.randomUUID());
        user.setRole("ADMIN");
        user.setStatus("NORMAL");
        users.insert(user);
        settings.setOperatorIds(Set.of(user.getId()));
        return tokenWithJti(user.getId(), RoleEnum.ADMIN, List.of(), UUID.randomUUID().toString());
    }

    private void transition(String token, DataExportResp task, String status, int version) throws Exception {
        mockMvc.perform(post("/api/mini/admin/compliance/requests/" + task.getTaskId() + "/transition")
                .header("Authorization", bearer(token)).contentType(JSON).content(transitionJson(status, version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(status));
    }

    private String transitionJson(String status, int version) {
        return "{\"status\":\"" + status + "\",\"expectedVersion\":" + version
                + ",\"evidenceRef\":\"ticket_123\""
                + ("PROCESSING".equals(status) ? ",\"dueAt\":" + (System.currentTimeMillis() + 86400000L) : "") + "}";
    }

    private String exportJson(FamilyContext ctx) {
        return "{\"childId\":\"" + childUserId(ctx) + "\"}";
    }

    private String deleteJson(FamilyContext ctx, String code, boolean confirmed) {
        return "{\"childId\":\"" + childUserId(ctx) + "\",\"confirmed\":" + confirmed + ",\"code\":\"" + code + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
