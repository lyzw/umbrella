package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

/** 运营端心愿菜单家庭配置（M3 心愿菜单配置，含乐观锁版本）。 */
@Data
@Builder
public class AdminWishConfigResp {
    private Long familyId;
    private Integer wishMenuEnabled;
    private Integer wishMenuMaxDishes;
    private Integer version;
}
