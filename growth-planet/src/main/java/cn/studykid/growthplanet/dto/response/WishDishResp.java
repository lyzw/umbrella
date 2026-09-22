package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

/**
 * 儿童心愿菜单可选菜谱目录项（P3）。
 * selectable=false 的菜照常返回并置灰（不静默隐藏，避免"菜不见了"的困惑）；
 * marked = 该 (childId, menuDate) 是否已在候选池。
 */
@Data
@Builder
public class WishDishResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long dishId;
    /** PRESET / FAMILY。 */
    private String type;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String categoryName;
    private String name;
    private String imageUrl;
    private String virtualPrice;
    private Integer spiceLevel;
    /** 上架状态（目录只返回 ON_SALE，保留字段以便与统一安全提示 safetyLabel 同口径）。 */
    private String status;
    private String allergenStatus;
    /** 与孩子档案比对后的安全判定：DECLARED / ALLERGY_CONFLICT / UNKNOWN（fail closed）。 */
    private String safetyStatus;
    private boolean allergyConflict;
    private boolean disliked;
    private boolean selectable;
    private boolean marked;
}
