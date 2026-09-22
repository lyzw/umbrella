package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 家长心愿菜单设置（P3）。无设置行时返回默认值 + version=0。
 */
@Data
@Builder
public class WishSettingResp {
    private int maxDishes;
    private boolean enabled;
    private int version;
}
