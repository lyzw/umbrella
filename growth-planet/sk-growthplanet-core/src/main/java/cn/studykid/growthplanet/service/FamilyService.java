package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.dto.request.BindApproveReq;
import cn.studykid.growthplanet.dto.request.CreateFamilyReq;
import cn.studykid.growthplanet.dto.request.GenerateInviteCodeReq;
import cn.studykid.growthplanet.dto.request.JoinFamilyReq;
import cn.studykid.growthplanet.dto.request.RemoveMemberReq;
import cn.studykid.growthplanet.dto.request.RevokeInviteCodeReq;
import cn.studykid.growthplanet.dto.request.TransferOwnerReq;
import cn.studykid.growthplanet.dto.response.BindApproveResp;
import cn.studykid.growthplanet.dto.response.CreateFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyInviteResp;
import cn.studykid.growthplanet.dto.response.InviteCodeResp;
import cn.studykid.growthplanet.dto.response.JoinFamilyResp;
import cn.studykid.growthplanet.dto.response.FamilyChildResp;
import cn.studykid.growthplanet.dto.response.MyFamilyResp;
import cn.studykid.growthplanet.dto.response.PageResp;
import java.util.List;

/**
 * FAMILY 模块业务接口：创建 / 邀请码 / 加入 / 绑定审批 / 成员管理 / 多家庭。
 */
public interface FamilyService {

    /** 创建家庭（PARENT）。 */
    CreateFamilyResp createFamily(CreateFamilyReq req);

    /** 获取/刷新邀请码（PARENT，已废弃：请使用 generateInviteCode / listInviteCodes）。 */
    @Deprecated
    InviteCodeResp getInviteCode();

    /** 生成邀请码（PARENT）：按 inviteType 锁定加入角色，默认 7 天有效、不限次数。 */
    FamilyInviteResp generateInviteCode(GenerateInviteCodeReq req);

    /** 列出本家庭有效邀请码（PARENT）。 */
    List<FamilyInviteResp> listInviteCodes();

    /** 撤销邀请码（PARENT）。 */
    void revokeInviteCode(RevokeInviteCodeReq req);

    /** 加入家庭（已登录任意角色，角色由邀请码类型决定）。 */
    JoinFamilyResp joinFamily(JoinFamilyReq req);

    /** 绑定审批（PARENT）。 */
    BindApproveResp bindApprove(BindApproveReq req);

    PageResp<FamilyChildResp> getChildren(int page, int pageSize, String bindStatus);

    FamilyChildResp getBinding();

    /** 返回当前用户所属的全部家庭（多家庭入口）。 */
    List<MyFamilyResp> myFamilies();

    /** 主动退出当前激活家庭（owner 不可直接退，须先转让）。 */
    void leaveFamily();

    /** 移除本家庭某成员（PARENT；不可移除 owner）。 */
    void removeMember(RemoveMemberReq req);

    /** 转让 owner 给本家庭内某 BOUND PARENT（仅当前 owner 可发起）。 */
    void transferOwner(TransferOwnerReq req);
}
