package com.growthplanet.service;

import com.growthplanet.dto.request.BindApproveReq;
import com.growthplanet.dto.request.CreateFamilyReq;
import com.growthplanet.dto.request.JoinFamilyReq;
import com.growthplanet.dto.response.BindApproveResp;
import com.growthplanet.dto.response.CreateFamilyResp;
import com.growthplanet.dto.response.InviteCodeResp;
import com.growthplanet.dto.response.JoinFamilyResp;

/**
 * FAMILY 模块业务接口：创建 / 邀请码 / 加入 / 绑定审批。
 */
public interface FamilyService {

    /** 创建家庭（PARENT）。 */
    CreateFamilyResp createFamily(CreateFamilyReq req);

    /** 获取/刷新邀请码（PARENT）。 */
    InviteCodeResp getInviteCode();

    /** 加入家庭（CHILD）。 */
    JoinFamilyResp joinFamily(JoinFamilyReq req);

    /** 绑定审批（PARENT）。 */
    BindApproveResp bindApprove(BindApproveReq req);
}
