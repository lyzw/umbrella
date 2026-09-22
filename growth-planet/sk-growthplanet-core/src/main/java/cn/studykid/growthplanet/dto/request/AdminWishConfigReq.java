package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 运营端更新心愿菜单家庭配置请求（M3 心愿菜单配置）：手写乐观锁（expectedVersion）。 */
@Data
public class AdminWishConfigReq {

    /** 0=关闭 1=开启 */
    @NotNull
    @Min(0)
    @Max(1)
    private Integer wishMenuEnabled;

    /** 每次心愿单最多可选菜品数（DDL 约束 1~10） */
    @NotNull
    @Min(1)
    @Max(10)
    private Integer wishMenuMaxDishes;

    @NotNull
    @Min(0)
    private Integer expectedVersion;
}
