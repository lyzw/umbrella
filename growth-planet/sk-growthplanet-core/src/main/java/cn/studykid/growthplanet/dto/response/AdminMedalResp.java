package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

/** 运营端勋章定义行（M3 勋章配置，含启停状态）。 */
@Data
@Builder
public class AdminMedalResp {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private String category;
    private String conditionType;
    private Integer threshold;
    private Integer sortOrder;
    private String status;
    private Long awardedCount;
}
