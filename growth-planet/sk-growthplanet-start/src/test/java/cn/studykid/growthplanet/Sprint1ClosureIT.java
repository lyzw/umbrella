package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.config.ComplianceProperties;
import cn.studykid.growthplanet.entity.AuditLog;
import cn.studykid.growthplanet.entity.ChildProfile;
import cn.studykid.growthplanet.entity.ConsentLog;
import cn.studykid.growthplanet.mapper.AuditLogMapper;
import cn.studykid.growthplanet.mapper.ChildProfileMapper;
import cn.studykid.growthplanet.mapper.ConsentLogMapper;
import cn.studykid.growthplanet.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Sprint1ClosureIT extends BaseIT {
    @Autowired ChildProfileMapper profiles;
    @Autowired AuditLogMapper audits;
    @Autowired ComplianceProperties policy;
    @Autowired ConsentLogMapper consents;
    @Autowired UserMapper users;

    @Test
    void parentDiscoversApplicationsWithScopedPaginationAndStatus() throws Exception {
        var ctx = setupFamily();
        var other = setupFamily();
        mockMvc.perform(get("/api/family/children").header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.items[0].childId").value(childUserId(ctx).toString()))
                .andExpect(jsonPath("$.data.items[0].applyId").value(ctx.applyId().toString()))
                .andExpect(jsonPath("$.data.items[0].bindStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].nickname").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].allergies").doesNotExist());
        for (String query : new String[]{"?page=2&pageSize=1", "?bindStatus=BOUND"}) {
            mockMvc.perform(get("/api/family/children" + query).header("Authorization", bearer(ctx.parentToken())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        }
        mockMvc.perform(get("/api/family/children").header("Authorization", bearer(other.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].childId").value(childUserId(other).toString()));
        for (String query : new String[]{"?page=0", "?pageSize=101", "?pageSize=0", "?page=1.5",
                "?bindStatus=UNKNOWN", "?page=2147483648"}) {
            mockMvc.perform(get("/api/family/children" + query).header("Authorization", bearer(ctx.parentToken())))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-400"));
        }
        mockMvc.perform(get("/api/family/children?page=2147483647&pageSize=100")
                .header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/family/children").header("Authorization", bearer(ctx.childToken())))
                .andExpect(status().isForbidden());
        String noFamily = loginAndSelectRole("p_" + UUID.randomUUID(), RoleEnum.PARENT);
        mockMvc.perform(get("/api/family/children").header("Authorization", bearer(noFamily)))
                .andExpect(status().isForbidden());
    }

    @Test
    void childCanRecoverBindingStateWithoutFamilyClaim() throws Exception {
        String fresh = loginAndSelectRole("c_" + UUID.randomUUID(), RoleEnum.CHILD);
        mockMvc.perform(get("/api/family/binding").header("Authorization", bearer(fresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bindStatus").value("NONE"));
        var ctx = setupFamily();
        assertBinding(ctx, "PENDING", 1);
        mockMvc.perform(post("/api/family/bind-approve").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content("{\"applyId\":\"" + ctx.applyId() + "\",\"approve\":false}"))
                .andExpect(status().isOk());
        assertBinding(ctx, "REJECTED", 1);
        mockMvc.perform(post("/api/family/join").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content("{\"inviteCode\":\"" + ctx.inviteCode() + "\"}"))
                .andExpect(status().isOk());
        assertBinding(ctx, "PENDING", 2);
        grant(ctx);
        approve(ctx);
        assertBinding(ctx, "BOUND", 2);
        mockMvc.perform(get("/api/family/binding").header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileReadRequiresParentBindingAndLiveConsent() throws Exception {
        var ctx = setupFamily();
        assertProfileStatus(ctx.parentToken(), childUserId(ctx), 403);
        grant(ctx);
        approve(ctx);
        assertProfileStatus(ctx.parentToken(), childUserId(ctx), 404);
        saveProfile(ctx);
        mockMvc.perform(get("/api/child/profile").param("childId", childUserId(ctx).toString())
                .header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.childId").value(childUserId(ctx).toString()))
                .andExpect(jsonPath("$.data.nickname").value("测试儿童"))
                .andExpect(jsonPath("$.data.allergies[0]").value("PEANUT"))
                .andExpect(jsonPath("$.data.profileStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.data.deleteAt").doesNotExist());
        assertProfileStatus(ctx.childToken(), childUserId(ctx), 403);
        var other = setupFamily();
        assertProfileStatus(other.parentToken(), childUserId(ctx), 403);
        assertProfileStatus(ctx.parentToken(), 0L, 400);
        mockMvc.perform(get("/api/child/profile").header("Authorization", bearer(ctx.parentToken())))
                .andExpect(status().isBadRequest());
        revoke(ctx);
        assertProfileStatus(ctx.parentToken(), childUserId(ctx), 409);
        grant(ctx);
        assertProfileStatus(ctx.parentToken(), childUserId(ctx), 200);
    }

    @Test
    void preferencesOnlyModifyOwnNonSafetyFieldsAndAllowParentRead() throws Exception {
        var ctx = readyProfile();
        String preferences = "{\"dislikes\":[\"芹菜\"],\"tastes\":[\"酸甜\"]}";
        mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content(preferences))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.childId").value(childUserId(ctx).toString()))
                .andExpect(jsonPath("$.data.dislikes[0]").value("芹菜"));
        for (String token : List.of(ctx.childToken(), ctx.parentToken())) {
            mockMvc.perform(get("/api/child/preferences").header("Authorization", bearer(token))
                    .param("childId", childUserId(ctx).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dislikes[0]").value("芹菜"))
                    .andExpect(jsonPath("$.data.tastes[0]").value("酸甜"))
                    .andExpect(jsonPath("$.data.allergies").doesNotExist())
                    .andExpect(jsonPath("$.data.school").doesNotExist());
        }
        ChildProfile profile = profile(ctx);
        assertEquals(List.of("PEANUT"), profile.getAllergies());
        assertEquals("测试儿童", profile.getNickname());
        assertEquals("三年级", profile.getGrade());
        assertEquals("合成学校", profile.getSchool());
        assertEquals(ctx.familyId(), profile.getFamilyId());
        assertEquals("COMPLETE", profile.getProfileStatus());
        var audit = audits.selectOne(new QueryWrapper<AuditLog>().eq("action", "PREFERENCES")
                .eq("actor_user_id", childUserId(ctx)));
        assertNotNull(audit);
        mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content("{\"dislikes\":[],\"tastes\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dislikes").isEmpty());
        assertEquals(List.of(), profile(ctx).getTastes());
    }

    @Test
    void preferencesRejectExtraFieldsMalformedValuesAndOtherUsers() throws Exception {
        var ctx = readyProfile();
        var other = readyProfile();
        for (String field : List.of("allergies", "familyId", "role", "childId", "nickname",
                "grade", "school", "favoriteDishIds", "profileStatus", "unexpected")) {
            String body = objectMapper.writeValueAsString(Map.of("dislikes", List.of(),
                    "tastes", List.of(), field, "injected"));
            mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                    .contentType(JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("E-400"));
        }
        for (String body : List.of("{}", "{\"dislikes\":null,\"tastes\":[]}",
                "{\"dislikes\":[null],\"tastes\":[]}", "{\"dislikes\":[\" \"],\"tastes\":[]}",
                "{\"dislikes\":[],\"tastes\":\"清淡\"}",
                objectMapper.writeValueAsString(Map.of("dislikes", Collections.nCopies(21, "x"), "tastes", List.of())),
                objectMapper.writeValueAsString(Map.of("dislikes", List.of(), "tastes", List.of("x".repeat(65)))))) {
            mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                    .contentType(JSON).content(body)).andExpect(status().isBadRequest());
        }
        for (String token : List.of(other.childToken(), other.parentToken())) {
            mockMvc.perform(get("/api/child/preferences").header("Authorization", bearer(token))
                    .param("childId", childUserId(ctx).toString())).andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content("{\"dislikes\":[],\"tastes\":[]}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                .param("childId", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/child/preferences")).andExpect(status().isUnauthorized());
        assertEquals(List.of("胡萝卜"), profile(ctx).getDislikes());
    }

    @Test
    void preferencesBlockBeforeBindingBeforeProfileAndAfterRevoke() throws Exception {
        var ctx = setupFamily();
        assertPreferencesStatus(ctx, 403);
        grant(ctx);
        approve(ctx);
        assertPreferencesStatus(ctx, 409);
        saveProfile(ctx);
        assertPreferencesStatus(ctx, 200);
        revoke(ctx);
        assertPreferencesStatus(ctx, 409);
        grant(ctx);
        assertPreferencesStatus(ctx, 200);
        String version = policy.getAgreementVersion();
        try {
            policy.setAgreementVersion("v2");
            assertPreferencesStatus(ctx, 409);
        } finally {
            policy.setAgreementVersion(version);
        }
        boolean collectionEnabled = policy.isCollectionEnabled();
        try {
            policy.setCollectionEnabled(false);
            assertPreferencesStatus(ctx, 403);
        } finally {
            policy.setCollectionEnabled(collectionEnabled);
        }
    }

    @Test
    void childPreferencesRequireActiveGuardianAndUnexpiredConsent() throws Exception {
        var ctx = readyProfile();
        var guardian = users.selectById(jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken())));
        guardian.setStatus("DISABLED");
        users.updateById(guardian);
        mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content("{\"dislikes\":[],\"tastes\":[]}"))
                .andExpect(status().isForbidden());
        guardian.setStatus("NORMAL");
        users.updateById(guardian);
        var consent = consents.selectOne(new QueryWrapper<ConsentLog>().eq("apply_id", ctx.applyId())
                .orderByDesc("id").last("LIMIT 1"));
        consent.setExpireAt(System.currentTimeMillis() - 1000);
        consents.updateById(consent);
        assertPreferencesStatus(ctx, 409);
        assertEquals(List.of("胡萝卜"), profile(ctx).getDislikes());
    }

    private FamilyContext readyProfile() throws Exception {
        var ctx = setupFamily();
        grant(ctx);
        approve(ctx);
        saveProfile(ctx);
        return ctx;
    }

    private void saveProfile(FamilyContext ctx) throws Exception {
        mockMvc.perform(post("/api/child/profile").header("Authorization", bearer(ctx.parentToken()))
                .contentType(JSON).content(profileJson(ctx))).andExpect(status().isOk());
    }

    private ChildProfile profile(FamilyContext ctx) {
        return profiles.selectOne(new QueryWrapper<ChildProfile>().eq("user_id", childUserId(ctx)));
    }

    private void assertBinding(FamilyContext ctx, String state, int version) throws Exception {
        mockMvc.perform(get("/api/family/binding").header("Authorization", bearer(ctx.childToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bindStatus").value(state))
                .andExpect(jsonPath("$.data.applicationVersion").value(version))
                .andExpect(jsonPath("$.data.applyId").value(ctx.applyId().toString()))
                .andExpect(jsonPath("$.data.familyId").value(ctx.familyId().toString()))
                .andExpect(jsonPath("$.data.inviteCode").doesNotExist());
    }

    private void assertProfileStatus(String token, Long childId, int expected) throws Exception {
        mockMvc.perform(get("/api/child/profile").header("Authorization", bearer(token))
                .param("childId", childId.toString())).andExpect(status().is(expected));
    }

    private void assertPreferencesStatus(FamilyContext ctx, int expected) throws Exception {
        mockMvc.perform(put("/api/child/preferences").header("Authorization", bearer(ctx.childToken()))
                .contentType(JSON).content("{\"dislikes\":[],\"tastes\":[]}"))
                .andExpect(status().is(expected));
        for (String token : List.of(ctx.childToken(), ctx.parentToken())) {
            mockMvc.perform(get("/api/child/preferences").header("Authorization", bearer(token))
                    .param("childId", childUserId(ctx).toString())).andExpect(status().is(expected));
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
