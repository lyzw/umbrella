package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.dto.request.ChildProfileReq;
import cn.studykid.growthplanet.dto.request.SelectRoleReq;
import cn.studykid.growthplanet.dto.request.WxLoginReq;
import cn.studykid.growthplanet.dto.response.ChildProfileResp;
import cn.studykid.growthplanet.dto.response.SelectRoleResp;
import cn.studykid.growthplanet.dto.response.WxLoginResp;

/**
 * AUTH 模块业务接口：微信登录 / 角色选择 / 儿童档案。
 */
public interface AuthService {
    void logout();

    /** 微信登录（公开）。 */
    WxLoginResp wxLogin(WxLoginReq req);

    /** 选择角色（重新签发 token）。 */
    SelectRoleResp selectRole(SelectRoleReq req);

    /** 提交/更新儿童档案。 */
    ChildProfileResp saveChildProfile(ChildProfileReq req);
}
