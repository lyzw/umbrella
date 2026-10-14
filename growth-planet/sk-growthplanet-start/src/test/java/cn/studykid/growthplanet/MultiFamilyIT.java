package cn.studykid.growthplanet;

import cn.studykid.growthplanet.common.enums.RoleEnum;
import cn.studykid.growthplanet.dto.request.GenerateInviteCodeReq;
import cn.studykid.growthplanet.dto.request.RemoveMemberReq;
import cn.studykid.growthplanet.dto.request.TransferOwnerReq;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyInviteResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.MyFamilyResp;
import cn.studykid.growthplanet.entity.FamilyMember;
import cn.studykid.growthplanet.mapper.FamilyMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 多家长 / 多学生 / 多家庭 重构集成测试：
 * 多家庭归属、家长邀请码免审、多家长审批、owner 转让、家庭切换（X-Family-Id）、退出 / 移除成员。
 */
class MultiFamilyIT extends BaseIT {

    @Autowired
    private FamilyMemberMapper familyMemberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 一个家长可创建 / 加入多个家庭，/family/my 返回全部。 */
    @Test
    void parentCanBelongToMultipleFamilies_andMyListsThem() throws Exception {
        String pCode = "mf_multi_" + UUID.randomUUID();
        String p = loginAndSelectRole(pCode, RoleEnum.PARENT);
        Long f1 = createFamily(p, "家庭A").getFamilyId();
        Long f2 = createFamily(p, "家庭B").getFamilyId();

        MvcResult my = mockMvc.perform(get("/api/mini/family/my")
                        .header("Authorization", "Bearer " + p))
                .andExpect(status().isOk()).andReturn();
        List<MyFamilyResp> families = dataOfList(my, MyFamilyResp.class);
        assertEquals(2, families.size());
        assertTrue(families.stream().anyMatch(f -> f.getFamilyId().equals(f1)));
        assertTrue(families.stream().anyMatch(f -> f.getFamilyId().equals(f2)));
    }

    /** 家长邀请码加入免审（BOUND）；家庭内任意 PARENT 均可审批学生申请。 */
    @Test
    void secondParentJoinsViaInvite_andBothParentsCanApprove() throws Exception {
        FamilyContext ctx = setupFamily();
        FamilyInviteResp invite = generateParentInvite(ctx.parentToken());

        String pbCode = "mf_pb_" + UUID.randomUUID();
        String parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT);
        JoinFamilyResp jb = joinWithCode(parentB, invite.getInviteCode());
        assertEquals("BOUND", jb.getStatus());
        // 家长 B 刷新 token 以携带家庭 A
        parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT);

        grant(ctx); // 家长 A 授予同意
        // 家长 B 也能审批通过（多家长：任意 BOUND PARENT 可审批）
        mockMvc.perform(post("/api/mini/family/bind-approve")
                        .header("Authorization", "Bearer " + parentB)
                        .contentType(JSON)
                        .content("{\"applyId\":" + ctx.applyId() + ",\"approve\":true}"))
                .andExpect(status().isOk());
    }

    /** X-Family-Id 指定当前激活家庭；不属于自己的家庭 → 403（跨家庭隔离）。 */
    @Test
    void xFamilyIdHeader_isolatesFamilies() throws Exception {
        String pCode = "mf_xh_" + UUID.randomUUID();
        String p = loginAndSelectRole(pCode, RoleEnum.PARENT);
        Long f1 = createFamily(p, "X家庭1").getFamilyId();
        Long f2 = createFamily(p, "X家庭2").getFamilyId();

        mockMvc.perform(get("/api/mini/family/children")
                        .header("Authorization", "Bearer " + p)
                        .header("X-Family-Id", String.valueOf(f2)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/mini/family/children")
                        .header("Authorization", "Bearer " + p)
                        .header("X-Family-Id", "999999"))
                .andExpect(status().isForbidden());
    }

    /** owner 转让：仅当前 owner 可发起；转让后新 owner 生效，原 owner 再转让被拒。 */
    @Test
    void transferOwner_movesOwnership_andOldOwnerBlocked() throws Exception {
        FamilyContext ctx = setupFamily();
        FamilyInviteResp invite = generateParentInvite(ctx.parentToken());
        String pbCode = "mf_to_" + UUID.randomUUID();
        String parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT);
        joinWithCode(parentB, invite.getInviteCode());
        parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT); // 刷新携带家庭 A

        Long parentBId = jwtUtil.getUserId(jwtUtil.parse(parentB));

        TransferOwnerReq transferReq = new TransferOwnerReq();
        transferReq.setTargetUserId(parentBId);
        mockMvc.perform(post("/api/mini/family/transfer-owner")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk());

        // 新 owner 校验
        parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT);
        assertTrue(isOwner(parentB, ctx.familyId()), "家长 B 应为新 owner");

        // 原 owner（家长 A）再次转让 → 403
        TransferOwnerReq transferReq2 = new TransferOwnerReq();
        transferReq2.setTargetUserId(parentBId);
        mockMvc.perform(post("/api/mini/family/transfer-owner")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(transferReq2)))
                .andExpect(status().isForbidden());
    }

    /** owner 不可直接退出；转让后非 owner 可退出。 */
    @Test
    void leaveFamily_ownerBlocked_nonOwnerAllowed() throws Exception {
        FamilyContext ctx = setupFamily();
        FamilyInviteResp invite = generateParentInvite(ctx.parentToken());
        String pbCode = "mf_lv_" + UUID.randomUUID();
        String parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT);
        joinWithCode(parentB, invite.getInviteCode());
        parentB = loginAndSelectRole(pbCode, RoleEnum.PARENT);

        // 家长 A 是当前 owner → 退出被拒
        mockMvc.perform(post("/api/mini/family/leave")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .header("X-Family-Id", String.valueOf(ctx.familyId())))
                .andExpect(status().isForbidden());

        // 转让给家长 B 后，家长 A 不再是 owner → 可退出
        Long parentBId = jwtUtil.getUserId(jwtUtil.parse(parentB));
        TransferOwnerReq transferReq = new TransferOwnerReq();
        transferReq.setTargetUserId(parentBId);
        mockMvc.perform(post("/api/mini/family/transfer-owner")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mini/family/leave")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .header("X-Family-Id", String.valueOf(ctx.familyId())))
                .andExpect(status().isOk());

        // 退出后 /family/my 不再含该家庭
        MvcResult my = mockMvc.perform(get("/api/mini/family/my")
                        .header("Authorization", "Bearer " + ctx.parentToken()))
                .andExpect(status().isOk()).andReturn();
        List<MyFamilyResp> families = dataOfList(my, MyFamilyResp.class);
        assertTrue(families.stream().noneMatch(f -> f.getFamilyId().equals(ctx.familyId())),
                "退出后 /family/my 仍包含已退出家庭");
    }

    /** 家长可移除学生成员；不可移除 owner。 */
    @Test
    void removeMember_removesChild_butNotOwner() throws Exception {
        FamilyContext ctx = setupFamily();
        grant(ctx);
        approve(ctx); // 儿童 BOUND，applyId 即其 member id

        RemoveMemberReq removeReq = new RemoveMemberReq();
        removeReq.setMemberId(ctx.applyId());
        mockMvc.perform(post("/api/mini/family/remove-member")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isOk());

        // 软删后 delete_at 被置为时间戳（非 0）；用 JdbcTemplate 直查原始行，绕过 MyBatis-Plus 全局逻辑删除过滤。
        Long removedDeleteAt = jdbcTemplate.queryForObject(
                "SELECT delete_at FROM usr_family_member WHERE id = ?", Long.class, ctx.applyId());
        assertTrue(removedDeleteAt != null && removedDeleteAt != 0L, "移除后成员未被软删");

        // 不可移除 owner
        FamilyMember ownerMember = familyMemberMapper.selectOne(new QueryWrapper<FamilyMember>()
                .eq("family_id", ctx.familyId())
                .eq("user_id", jwtUtil.getUserId(jwtUtil.parse(ctx.parentToken())))
                .eq("delete_at", 0));
        RemoveMemberReq removeOwnerReq = new RemoveMemberReq();
        removeOwnerReq.setMemberId(ownerMember.getId());
        mockMvc.perform(post("/api/mini/family/remove-member")
                        .header("Authorization", "Bearer " + ctx.parentToken())
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(removeOwnerReq)))
                .andExpect(status().isForbidden());
    }

    // ===== 辅助方法 =====

    private CreateFamilyResp createFamily(String token, String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/mini/family/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"familyName\":\"" + name + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return dataOf(r, CreateFamilyResp.class);
    }

    private FamilyInviteResp generateParentInvite(String token) throws Exception {
        GenerateInviteCodeReq req = new GenerateInviteCodeReq();
        req.setInviteType("PARENT");
        MvcResult r = mockMvc.perform(post("/api/mini/family/invite-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        return dataOf(r, FamilyInviteResp.class);
    }

    private JoinFamilyResp joinWithCode(String token, String code) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/mini/family/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(JSON)
                        .content("{\"inviteCode\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return dataOf(r, JoinFamilyResp.class);
    }

    private boolean isOwner(String token, Long familyId) throws Exception {
        MvcResult my = mockMvc.perform(get("/api/mini/family/my")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        List<MyFamilyResp> families = dataOfList(my, MyFamilyResp.class);
        return families.stream()
                .filter(f -> f.getFamilyId().equals(familyId))
                .anyMatch(f -> Boolean.TRUE.equals(f.getIsOwner()));
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> dataOfList(MvcResult result, Class<T> clazz) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return objectMapper.convertValue(root.get("data"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }
}
