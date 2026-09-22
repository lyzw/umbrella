package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 运营端每日菜单行（M3 菜单编排列表，SCHOOL 为主，可按需扩展 FAMILY 视图）。 */
@Data
@Builder
public class AdminMenuRowResp {
    private Long id;
    private String sourceType;
    private String ownerKey;
    private String school;
    private Long familyId;
    private LocalDate menuDate;
    private String mealType;
    private String status;
    /** 菜品引用列表（type=PRESET/FAMILY + id），前端按菜品库解析名称 */
    private List<cn.studykid.growthplanet.entity.DishRef> dishIds;
    private Integer version;
}
