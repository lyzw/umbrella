package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.entity.Notice;
import cn.studykid.growthplanet.mapper.NoticeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ConsentLog;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ConsentLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplianceFlowIT extends BaseIT {
    @Autowired ChildProfileMapper profiles;
    @Autowired ConsentLogMapper consents;
    @Autowired
    ComplianceProperties policy;
    @Autowired
    NoticeMapper notices;

    @Test
    void expiredVersionChangedAndInsufficientVerificationBlockProcessing() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        policy.setSelfAttestationAccepted(false);
        try {
            mockMvc.perform(post("/api/family/bind-approve").header("Authorization", "Bearer " + ctx.parentToken())
                    .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                    .andExpect(status().isConflict());
        } finally {
            policy.setSelfAttestationAccepted(true);
        }
        approve(ctx);
        policy.setAgreementVersion("v2");
        try {
            mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                    .contentType(JSON).content(profileJson(ctx))).andExpect(status().isConflict());
            mockMvc.perform(get("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                    .param("childId", childUserId(ctx).toString()).param("consentType", "PROFILE"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStatus").value("VERSION_CHANGED"));
        } finally {
            policy.setAgreementVersion("v1");
        }
        for (String invalid : new String[]{profileJson(ctx).replace("三年级", "未发布年级"),
                profileJson(ctx).replace("PEANUT", "UNKNOWN_CODE")}) {
            mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                    .contentType(JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        consents.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ConsentLog>()
                .eq("child_id", childUserId(ctx)).set("expire_at", 1));
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isConflict());
        assertEquals(0, profiles.selectCount(new QueryWrapper<ChildProfile>().eq("user_id", childUserId(ctx))));
    }

    @Test
    void exportKeyCannotBeReusedForAnotherChild() throws Exception {
        var first = setupFamily();
        grant(first);
        approve(first);
        String childToken = loginAndSelectRole(java.util.UUID.randomUUID().toString(),
                RoleEnum.CHILD);
        var join = mockMvc.perform(post("/api/family/join").header("Authorization", "Bearer " + childToken)
                .contentType(JSON).content("{\"inviteCode\":\"" + first.inviteCode() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        var second = new FamilyContext(first.parentToken(), childToken, first.familyId(), first.inviteCode(),
                dataOf(join, JoinFamilyResp.class).getApplyId());
        grant(second);
        approve(second);
        mockMvc.perform(post("/api/compliance/data-export").header("Authorization", "Bearer " + first.parentToken())
                .header("Idempotency-Key", "shared-key").contentType(JSON)
                .content("{\"childId\":\"" + childUserId(first) + "\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/data-export").header("Authorization", "Bearer " + first.parentToken())
                .header("Idempotency-Key", "shared-key").contentType(JSON)
                .content("{\"childId\":\"" + childUserId(second) + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-012"));
    }

    @Test
    void consentIsScopedAndSelfAttestationIsNotVerification() throws Exception {
        var ctx = setupFamily();
        mockMvc.perform(post("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(consentJson(ctx)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.guardianStatus").value("SELF_ATTESTED"));
        mockMvc.perform(get("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .param("childId", childUserId(ctx).toString()).param("consentType", "PROFILE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStatus").value("GRANTED"));
        grant(ctx);
        assertEquals(1, consents.selectCount(new QueryWrapper<ConsentLog>().eq("child_id", childUserId(ctx))));
    }

    @Test
    void invalidAgeAgreementVersionOrOwnershipCannotGrant() throws Exception {
        var ctx = setupFamily();
        for (String invalid : new String[]{
                consentJson(ctx).replace(":25", ":17"), consentJson(ctx).replace(":25", ":121"),
                consentJson(ctx).replace(":25", ":25.5"),
                consentJson(ctx).replace(":true", ":false"),
                consentJson(ctx).replace("\"PROFILE\"", "\"ORDER\"")}) {
            mockMvc.perform(post("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                    .contentType(JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(consentJson(ctx).replace("\"v1\"", "\"old\"")))
                .andExpect(status().isConflict());
        var other = setupFamily();
        mockMvc.perform(post("/api/compliance/consent").header("Authorization", "Bearer " + other.parentToken())
                .contentType(JSON).content(consentJson(ctx))).andExpect(status().isForbidden());
        assertEquals(0, consents.selectCount(new QueryWrapper<ConsentLog>().eq("child_id", childUserId(ctx))));
    }

    @Test
    void approvalRequiresConsentAndProfileRequiresBoundParent() throws Exception {
        var ctx = setupFamily();
        mockMvc.perform(post("/api/family/bind-approve").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-010"));
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isForbidden());
        grant(ctx);
        approve(ctx);
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.childToken())
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
        ChildProfile profile = profiles.selectOne(new QueryWrapper<ChildProfile>().eq("user_id", childUserId(ctx)));
        assertEquals(java.util.List.of("PEANUT"), profile.getAllergies());
        assertEquals(java.util.List.of("胡萝卜"), profile.getDislikes());
        assertEquals(java.util.List.of("清淡"), profile.getTastes());
    }

    @Test
    void revokeAppendsHistoryBlocksWritesAndPreservesParentRights() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        notices.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Notice>()
                .eq("child_id", childUserId(ctx)).eq("channel", "SUBSCRIBE").set("status", "PENDING"));
        revoke(ctx);
        revoke(ctx);
        var logs = consents.selectList(new QueryWrapper<ConsentLog>()
                .eq("child_id", childUserId(ctx)).orderByAsc("id"));
        assertEquals(java.util.List.of("GRANT", "REVOKE"), logs.stream().map(ConsentLog::getAction).toList());
        assertEquals(0, notices.selectCount(new QueryWrapper<Notice>()
                .eq("child_id", childUserId(ctx)).eq("channel", "SUBSCRIBE").eq("status", "PENDING")));
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(profileJson(ctx)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("E-010"));
        mockMvc.perform(get("/api/compliance/consent").header("Authorization", "Bearer " + ctx.parentToken())
                .param("childId", childUserId(ctx).toString()).param("consentType", "PROFILE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentStatus").value("REVOKED"));
        mockMvc.perform(post("/api/compliance/data-export").header("Authorization", "Bearer " + ctx.parentToken())
                .header("Idempotency-Key", "revoked-rights-export")
                .contentType(JSON).content("{\"childId\":\"" + childUserId(ctx) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.downloadAvailable").value(false)).andExpect(jsonPath("$.data.data").doesNotExist());
        grant(ctx);
        mockMvc.perform(post("/api/child/profile").header("Authorization", "Bearer " + ctx.parentToken())
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
        assertEquals(java.util.List.of("GRANT", "REVOKE", "GRANT"), consents.selectList(
                new QueryWrapper<ConsentLog>().eq("child_id", childUserId(ctx)).orderByAsc("id"))
                .stream().map(ConsentLog::getAction).toList());
    }

    @Test
    void exportIsDurableIdempotentAndPrivate() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        String taskId = null;
        for (int i = 0; i < 2; i++) {
            var result = mockMvc.perform(post("/api/compliance/data-export")
                    .header("Authorization", "Bearer " + ctx.parentToken()).header("Idempotency-Key", "export_1")
                    .contentType(JSON).content("{\"childId\":\"" + childUserId(ctx) + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").isString()).andReturn();
            String currentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("taskId").asText();
            if (taskId != null) assertEquals(taskId, currentId);
            taskId = currentId;
        }
        mockMvc.perform(get("/api/compliance/requests/" + taskId)
                .header("Authorization", "Bearer " + ctx.parentToken())).andExpect(status().isOk());
        var other = setupFamily();
        mockMvc.perform(get("/api/compliance/requests/" + taskId)
                .header("Authorization", "Bearer " + other.parentToken())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/compliance/data-export").header("Authorization", "Bearer " + other.parentToken())
                .header("Idempotency-Key", "cross-family-export")
                .contentType(JSON).content("{\"childId\":\"" + childUserId(ctx) + "\"}")).andExpect(status().isForbidden());
    }
}
