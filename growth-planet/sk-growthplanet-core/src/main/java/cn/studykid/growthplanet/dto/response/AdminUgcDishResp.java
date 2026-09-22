package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 运营端 UGC 审核队列行（家庭私有菜品，含过敏原声明标记与审核留痕）。 */
@Data
@Builder
public class AdminUgcDishResp {
    private Long id;
    private Long familyId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String imageUrl;
    private BigDecimal virtualPrice;
    private Integer calories;
    private String tags;
    private List<String> allergens;
    private String allergenStatus;
    private Integer spiceLevel;
    private String status;
    private String visibility;
    private String reviewStatus;
    private String rejectReason;
    private Integer version;
    private LocalDateTime createTime;
}
